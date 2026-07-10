package org.example.quickcode.langgraph4j;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.core.LangChain4jStreamUtils;
import org.example.quickcode.langgraph4j.WorkflowExecutionHolder;
import org.example.quickcode.langgraph4j.model.QualityResult;
import org.example.quickcode.langgraph4j.model.enums.ImageCollectionMode;
import org.example.quickcode.langgraph4j.node.*;
import org.example.quickcode.langgraph4j.state.WorkflowContext;
import org.example.quickcode.model.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
@Service
public class CodeGenWorkflow {

    private static final int MAX_QUALITY_RETRIES = 2;

    private CompiledGraph<MessagesState<String>> compiledWorkflow;

    @PostConstruct
    public void init() {
        compiledWorkflow = createWorkflow();
    }

    /**
     * 集成到主业务的流式工作流入口
     */
    public Flux<String> executeForApp(Long appId, String message, CodeGenTypeEnum codeGenType) {
        return Flux.create(sink -> Thread.startVirtualThread(() -> {
            try {
                WorkflowExecutionHolder.set(appId, codeGenType, sink);

                WorkflowContext initialContext = WorkflowContext.builder()
                        .appId(appId)
                        .originalPrompt(message)
                        .generationType(codeGenType)
                        .currentStep("初始化")
                        .build();

                log.info("开始执行代码生成工作流, appId={}, type={}", appId, codeGenType.getValue());
                GraphRepresentation graph = compiledWorkflow.getGraph(GraphRepresentation.Type.MERMAID);
                log.debug("工作流图:\n{}", graph.content());

                for (NodeOutput<MessagesState<String>> step : compiledWorkflow.stream(
                        Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        log.info("工作流步骤完成: {}", currentContext.getCurrentStep());
                    }
                }

                log.info("代码生成工作流执行完成, appId={}", appId);
                WorkflowExecutionHolder.emitStatus(appId, "✅ 代码生成已完成，请查看右侧预览");
                sink.complete();
            } catch (Exception e) {
                if (LangChain4jStreamUtils.isBenignNullResponseError(e)) {
                    log.warn("工作流收尾异常已忽略, appId={}: {}", appId, e.getMessage());
                    WorkflowExecutionHolder.emitStatus(appId, "✅ 代码生成已完成，请查看右侧预览");
                    sink.complete();
                } else {
                    log.error("工作流执行失败, appId={}: {}", appId, e.getMessage(), e);
                    WorkflowExecutionHolder.emitStatus(appId, "代码生成失败：" + LangChain4jStreamUtils.resolveFriendlyErrorMessage(e));
                    sink.complete();
                }
            } finally {
                WorkflowExecutionHolder.clear(appId);
            }
        }), FluxSink.OverflowStrategy.BUFFER);
    }

    private CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    .addNode("image_intent_router", ImageIntentRouterNode.create())
                    .addNode("image_collector", ImageCollectorNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
//                    .addNode("code_quality_check", CodeQualityCheckNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())
                    .addEdge(START, "image_intent_router")
                    .addConditionalEdges("image_intent_router",
                            edge_async(this::routeAfterImageIntent),
                            Map.of(
                                    "skip", "prompt_enhancer",
                                    "collect", "image_collector"
                            ))
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    // 跳过代码审查阶段，代码生成后直接进入构建或结束
                    .addConditionalEdges("code_generator",
                            edge_async(this::routeBuildOrSkip),
                            Map.of(
                                    "build", "project_builder",
                                    "skip_build", END
                            ))
//                    .addEdge("code_generator", "code_quality_check")
//                    .addConditionalEdges("code_quality_check",
//                            edge_async(this::routeAfterQualityCheck),
//                            Map.of(
//                                    "build", "project_builder",
//                                    "skip_build", END,
//                                    "fail", "code_generator"
//                            ))
                    .addEdge("project_builder", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工作流创建失败");
        }
    }

    private String routeAfterImageIntent(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context.getImageCollectionMode() == ImageCollectionMode.SKIP) {
            return "skip";
        }
        return "collect";
    }

    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();
        if (qualityResult == null || !qualityResult.getIsValid()) {
            if (context.getQualityCheckRetryCount() >= MAX_QUALITY_RETRIES) {
                log.warn("代码质检重试次数已达上限({})，继续后续流程", MAX_QUALITY_RETRIES);
                return routeBuildOrSkip(state);
            }
            log.warn("代码质检失败，准备第 {} 次重新生成", context.getQualityCheckRetryCount() + 1);
            return "fail";
        }
        log.info("代码质检通过，继续后续流程");
        return routeBuildOrSkip(state);
    }

    private String routeBuildOrSkip(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == CodeGenTypeEnum.HTML || generationType == CodeGenTypeEnum.MULTI_FILE) {
            return "skip_build";
        }
        return "build";
    }
}
