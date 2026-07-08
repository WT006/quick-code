package org.example.quickcode.langgraph4j.node;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.example.quickcode.utils.SpringContextUtil;
import org.example.quickcode.constant.AppConstant;
import org.example.quickcode.core.AiCodeGeneratorFacade;
import org.example.quickcode.core.LangChain4jStreamUtils;
import org.example.quickcode.langgraph4j.WorkflowExecutionHolder;
import org.example.quickcode.langgraph4j.model.QualityResult;
import org.example.quickcode.langgraph4j.state.WorkflowContext;
import org.example.quickcode.model.enums.CodeGenTypeEnum;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.Duration;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");

            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum generationType = context.getGenerationType();
            Long appId = resolveAppId(context);

            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，appId={}, 类型: {} ({})", appId, generationType.getValue(), generationType.getText());

            if (context.getQualityCheckRetryCount() > 0) {
                WorkflowExecutionHolder.emitStatus(appId,
                        String.format("代码审查未通过，正在第 %d 次重新生成...", context.getQualityCheckRetryCount()));
                WorkflowExecutionHolder.emitContentReset(appId);
            } else if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
                WorkflowExecutionHolder.emitStatus(appId, "正在生成 Vue 项目代码");
            }

            boolean freshSession = generationType == CodeGenTypeEnum.VUE_PROJECT
                    || context.getQualityCheckRetryCount() > 0;
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(
                    userMessage, generationType, appId, freshSession);
            try {
                codeStream
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .blockLast(Duration.ofMinutes(20));
            } catch (Exception e) {
                if (LangChain4jStreamUtils.isBenignNullResponseError(e)) {
                    log.warn("代码生成流收尾异常已忽略: {}", e.getMessage());
                } else {
                    throw e;
                }
            }

            String generatedCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);
            File outputDir = new File(generatedCodeDir);
            if (!outputDir.exists() || !outputDir.isDirectory()) {
                throw new IllegalStateException("代码生成失败：输出目录不存在 " + generatedCodeDir);
            }
            log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

            context.setCurrentStep("代码生成");
            context.setGeneratedCodeDir(generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    private static Long resolveAppId(WorkflowContext context) {
        if (context.getAppId() != null) {
            return context.getAppId();
        }
        throw new IllegalStateException("工作流缺少 appId，无法生成代码");
    }

    private static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            userMessage = userMessage + buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null &&
                !qualityResult.getIsValid() &&
                qualityResult.getErrors() != null &&
                !qualityResult.getErrors().isEmpty();
    }

    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }
}
