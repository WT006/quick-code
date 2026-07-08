package org.example.quickcode.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.ai.AiCodeGeneratorService;
import org.example.quickcode.ai.AiCodeGeneratorServiceFactory;
import org.example.quickcode.ai.model.HtmlCodeResult;
import org.example.quickcode.ai.model.MultiFileCodeResult;
import org.example.quickcode.ai.model.message.AiResponseMessage;
import org.example.quickcode.core.stream.StreamToolMessageFormatter;
import org.example.quickcode.ai.model.message.ToolExecutedMessage;
import org.example.quickcode.ai.model.message.ToolRequestMessage;
import org.example.quickcode.langgraph4j.WorkflowExecutionHolder;
import org.example.quickcode.constant.AppConstant;
import org.example.quickcode.core.builder.VueProjectBuilder;
import org.example.quickcode.core.MultiFilePhasedStreamSupport.Phase;
import org.example.quickcode.core.stream.CodegenStepTextFilter;
import org.example.quickcode.core.stream.StreamContentNormalizer;
import org.example.quickcode.core.parser.CodeParserExecutor;
import org.example.quickcode.core.saver.CodeFileSaverExecutor;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.model.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private StreamToolMessageFormatter streamToolMessageFormatter;
    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        //根据AppId获取相应AI服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML ->{
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeParser(result, codeGenTypeEnum,appId);
            }
            case MULTI_FILE ->{
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeParser(result, codeGenTypeEnum,appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码(流式输出)
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        return generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId, false);
    }

    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, boolean freshSession) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // Vue 单次生成需 fresh session；多文件质检重试时也需 fresh session，避免历史对话干扰
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, freshSession);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> processMultiFilePhasedStream(aiCodeGeneratorService, userMessage, appId);
            case VUE_PROJECT -> {
                TokenStream codeStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                // Vue 通过工具调用实时写文件，流内容为 JSON 消息，不走 HTML/CSS/JS 解析保存
                yield processTokenStream(codeStream, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 流式处理代码生成结果
     *
     * @param result       代码生成结果
     * @param codeGenType 生成类型
     * @return 处理后的代码
     */
    private Flux<String> processCodeStream(Flux<String> result, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return result
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (StrUtil.isBlank(chunk)) {
                        return;
                    }
                    synchronized (codeBuilder) {
                        codeBuilder.append(chunk);
                    }
                    String displayChunk = CodegenStepTextFilter.filterStreamChunk(chunk);
                    if (StrUtil.isNotBlank(displayChunk) && WorkflowExecutionHolder.isActive(appId)) {
                        WorkflowExecutionHolder.emitContent(appId, displayChunk);
                    }
                })
                .concatWith(Flux.defer(() -> {
                    try {
                        String completeCode;
                        synchronized (codeBuilder) {
                            completeCode = codeBuilder.toString();
                        }
                        saveStreamedCode(completeCode, codeGenType, appId);
                        return Flux.empty();
                    } catch (Exception e) {
                        log.error("生成代码失败：{}", e.getMessage(), e);
                        if (WorkflowExecutionHolder.isActive(appId)) {
                            WorkflowExecutionHolder.emitStatus(appId, "代码保存失败：" + e.getMessage());
                        }
                        return Flux.error(e);
                    }
                }));
    }

    /**
     * 多文件模式：仿照 HTML 单块流式，分 HTML / CSS / JS 三阶段依次生成并流式展示。
     */
    private Flux<String> processMultiFilePhasedStream(AiCodeGeneratorService service, String userMessage, Long appId) {
        MultiFileCodeResult accumulated = new MultiFileCodeResult();
        return Flux.concat(
                        runMultiFilePhase(Phase.HTML, service, userMessage, appId, accumulated),
                        runMultiFilePhase(Phase.CSS, service, userMessage, appId, accumulated),
                        runMultiFilePhase(Phase.JS, service, userMessage, appId, accumulated),
                        Flux.defer(() -> {
                            try {
                                if (WorkflowExecutionHolder.isActive(appId)) {
                                    WorkflowExecutionHolder.emitStatus(appId, "代码文件已生成");
                                }
                                MultiFilePhasedStreamSupport.validateBeforeSave(accumulated);
                                CodeFileSaverExecutor.executeParser(accumulated, CodeGenTypeEnum.MULTI_FILE, appId);
                                log.info("多文件分阶段保存成功, appId={}", appId);
                                return Flux.empty();
                            } catch (Exception e) {
                                log.error("多文件分阶段保存失败：{}", e.getMessage(), e);
                                if (WorkflowExecutionHolder.isActive(appId)) {
                                    WorkflowExecutionHolder.emitStatus(appId, "代码保存失败：" + e.getMessage());
                                }
                                return Flux.error(e);
                            }
                        }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<String> runMultiFilePhase(Phase phase,
                                            AiCodeGeneratorService service,
                                            String userMessage,
                                            Long appId,
                                            MultiFileCodeResult accumulated) {
        StringBuilder phaseBuilder = new StringBuilder();
        // 必须使用多文件流式通道，避免 HTML 单文件提示词要求内联 CSS/JS 导致质检失败
        Flux<String> phaseStream = service.generateMultiFileCodeStream(
                MultiFilePhasedStreamSupport.buildPhasePrompt(phase, userMessage));

        return Flux.concat(
                Flux.defer(() -> {
                    if (WorkflowExecutionHolder.isActive(appId)) {
                        WorkflowExecutionHolder.emitStatus(appId, phase.stepText());
                    }
                    return Flux.empty();
                }),
                phaseStream.doOnNext(chunk -> appendPhaseChunk(chunk, phaseBuilder, appId)),
                Flux.defer(() -> {
                    MultiFilePhasedStreamSupport.applyPhaseResult(phase, phaseBuilder.toString(), accumulated);
                    log.info("多文件阶段完成: {}, appId={}, 阶段长度={}", phase.label(), appId, phaseBuilder.length());
                    return Flux.empty();
                })
        );
    }

    private void appendPhaseChunk(String chunk, StringBuilder phaseBuilder, Long appId) {
        if (StrUtil.isBlank(chunk)) {
            return;
        }
        phaseBuilder.append(chunk);
        String displayChunk = CodegenStepTextFilter.filterStreamChunk(chunk);
        if (StrUtil.isNotBlank(displayChunk)) {
            appendAndEmitContent(appId, displayChunk);
        }
    }

    private void appendAndEmitContent(Long appId, String chunk) {
        if (WorkflowExecutionHolder.isActive(appId)) {
            WorkflowExecutionHolder.emitContent(appId, chunk);
        }
    }

    private void saveStreamedCode(String completeCode, CodeGenTypeEnum codeGenType, Long appId) {
        log.info("【排查】生成类型={}, 流式内容长度={}", codeGenType, completeCode.length());
        if (completeCode.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "AI 未返回任何内容，请检查模型 API 是否正常或稍后重试");
        }
        int headLen = Math.min(500, completeCode.length());
        log.info("【排查】流式内容前{}字:\n{}", headLen, completeCode.substring(0, headLen));
        if (completeCode.length() > 500) {
            log.info("【排查】流式内容后500字:\n{}", completeCode.substring(completeCode.length() - 500));
        }
        Object parsedResult = CodeParserExecutor.executeParser(
                StreamContentNormalizer.normalize(completeCode), codeGenType);
        if (codeGenType == CodeGenTypeEnum.HTML) {
            HtmlCodeResult htmlResult = (HtmlCodeResult) parsedResult;
            if (StrUtil.isBlank(htmlResult.getHtmlCode())) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "未能从 AI 响应中解析出 HTML 代码（响应长度 " + completeCode.length() + " 字）");
            }
        }
        File file = CodeFileSaverExecutor.executeParser(parsedResult, codeGenType, appId);
        log.info("保存成功，保存目录为：{}", file.getAbsolutePath());
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        return Flux.<String>create(sink -> {
            AtomicBoolean finished = new AtomicBoolean(false);
            Set<String> seenToolIds = new HashSet<>();
            tokenStream.onPartialResponse(partialResponse -> {
                        String json = JSONUtil.toJsonStr(new AiResponseMessage(partialResponse));
                        sink.next(json);
                        if (WorkflowExecutionHolder.isActive(appId) && StrUtil.isNotBlank(partialResponse)) {
                            WorkflowExecutionHolder.emitContent(appId, partialResponse);
                        }
                    })
                    .beforeToolExecution(beforeToolExecution -> {
                        String json = JSONUtil.toJsonStr(new ToolRequestMessage(beforeToolExecution.request()));
                        sink.next(json);
                        if (WorkflowExecutionHolder.isActive(appId)) {
                            ToolRequestMessage toolRequestMessage = JSONUtil.toBean(json, ToolRequestMessage.class);
                            String display = streamToolMessageFormatter.formatToolRequest(toolRequestMessage, seenToolIds);
                            if (StrUtil.isNotBlank(display)) {
                                WorkflowExecutionHolder.emitTool(appId, display);
                            }
                        }
                    })
                    .onToolExecuted(toolExecution -> {
                        String json = JSONUtil.toJsonStr(new ToolExecutedMessage(toolExecution));
                        sink.next(json);
                        if (WorkflowExecutionHolder.isActive(appId)) {
                            ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(json, ToolExecutedMessage.class);
                            String display = streamToolMessageFormatter.formatToolExecuted(toolExecutedMessage, appId);
                            if (StrUtil.isNotBlank(display)) {
                                WorkflowExecutionHolder.emitTool(appId, display);
                            }
                        }
                    })
                    .onCompleteResponse(response -> completeTokenStream(finished, sink, appId, response))
                    .onError(error -> handleTokenStreamError(finished, sink, appId, error))
                    .start();
        }).onErrorResume(error -> {
            if (LangChain4jStreamUtils.isBenignNullResponseError(error)) {
                log.warn("Vue 流式生成收尾异常，已忽略并继续: {}", error.getMessage());
                return Flux.empty();
            }
            return Flux.error(error);
        });
    }

    private void completeTokenStream(AtomicBoolean finished, FluxSink<String> sink, Long appId, ChatResponse response) {
        if (response == null) {
            log.warn("onCompleteResponse 收到 null ChatResponse，工具调用可能已完成，按成功收尾处理");
        }
        finishTokenStream(finished, sink, appId);
    }

    private void handleTokenStreamError(AtomicBoolean finished, FluxSink<String> sink, Long appId, Throwable error) {
        if (LangChain4jStreamUtils.isBenignNullResponseError(error)) {
            log.warn("Vue Agent 流式结束异常（{}），工具调用可能已完成，按成功收尾处理", error.getMessage());
            finishTokenStream(finished, sink, appId);
            return;
        }
        if (finished.compareAndSet(false, true)) {
            log.error("Vue 代码生成流式失败", error);
            sink.error(error);
        }
    }

    private void finishTokenStream(AtomicBoolean finished, FluxSink<String> sink, Long appId) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        if (!WorkflowExecutionHolder.isActive(appId)) {
            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
            vueProjectBuilder.buildProjectAsync(projectPath);
        }
        sink.complete();
    }

}
