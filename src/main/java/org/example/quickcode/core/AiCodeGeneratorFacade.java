package org.example.quickcode.core;

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
import org.example.quickcode.ai.model.message.ToolExecutedMessage;
import org.example.quickcode.ai.model.message.ToolRequestMessage;
import org.example.quickcode.langgraph4j.WorkflowExecutionHolder;
import org.example.quickcode.constant.AppConstant;
import org.example.quickcode.core.builder.VueProjectBuilder;
import org.example.quickcode.core.parser.CodeParserExecutor;
import org.example.quickcode.core.saver.CodeFileSaverExecutor;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;
import org.example.quickcode.model.enums.CodeGenTypeEnum;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.File;
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
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // Vue 单次生成需 fresh session，避免缓存中残留的工具调用消息污染请求
        boolean freshSession = codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT;
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum, freshSession);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
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
                .doOnNext(codeBuilder::append)
                .concatWith(Flux.defer(() -> {
                    try {
                        saveStreamedCode(codeBuilder.toString(), codeGenType, appId);
                        return Flux.empty();
                    } catch (Exception e) {
                        log.error("生成代码失败：{}", e.getMessage(), e);
                        return Flux.error(e);
                    }
                }));
    }

    private void saveStreamedCode(String completeCode, CodeGenTypeEnum codeGenType, Long appId) {
        log.info("【排查】生成类型={}, 流式内容长度={}", codeGenType, completeCode.length());
        if (completeCode.isEmpty()) {
            log.warn("【排查】流式内容为空");
        } else {
            int headLen = Math.min(500, completeCode.length());
            log.info("【排查】流式内容前{}字:\n{}", headLen, completeCode.substring(0, headLen));
            if (completeCode.length() > 500) {
                log.info("【排查】流式内容后500字:\n{}", completeCode.substring(completeCode.length() - 500));
            }
        }
        Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
        File file = CodeFileSaverExecutor.executeParser(parsedResult, codeGenType, appId);
        log.info("保存成功，保存目录为：{}", file.getAbsolutePath());
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        return Flux.<String>create(sink -> {
            AtomicBoolean finished = new AtomicBoolean(false);
            tokenStream.onPartialResponse(partialResponse -> {
                        String json = JSONUtil.toJsonStr(new AiResponseMessage(partialResponse));
                        sink.next(json);
                        if (WorkflowExecutionHolder.isActive(appId)) {
                            WorkflowExecutionHolder.emitRaw(appId, json);
                        }
                    })
                    .beforeToolExecution(beforeToolExecution -> {
                        String json = JSONUtil.toJsonStr(new ToolRequestMessage(beforeToolExecution.request()));
                        sink.next(json);
                        if (WorkflowExecutionHolder.isActive(appId)) {
                            WorkflowExecutionHolder.emitRaw(appId, json);
                        }
                    })
                    .onToolExecuted(toolExecution -> {
                        String json = JSONUtil.toJsonStr(new ToolExecutedMessage(toolExecution));
                        sink.next(json);
                        if (WorkflowExecutionHolder.isActive(appId)) {
                            WorkflowExecutionHolder.emitRaw(appId, json);
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
