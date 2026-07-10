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
import org.example.quickcode.core.stream.CodegenIncrementalStreamFilter;
import org.example.quickcode.core.stream.CodegenIncrementalStreamFilter.State;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    /** 单轮 LangChain4j 工具上限为 5，续跑时最多再跑若干轮 */
    private static final int MAX_VUE_CONTINUATION_ROUNDS = 10;

    private static final String VUE_CONTINUATION_USER_MESSAGE = """
            请继续完成 Vue 项目生成（上一轮因单轮工具次数达上限而暂停，已写入的文件请勿重复创建）。
            要求：
            1）先用 readDir 查看当前项目结构，确认哪些文件尚未完成
            2）只创建/修改尚未完成的业务页面、组件、路由和样式；禁止重写 package.json、vite.config.js、index.html、src/main.js
            3）单轮最多调用 4 次工具，优先 writeFile 新页面；修改已有脚手架文件用 modifyFile
            4）若已全部完成，仅用 1 行文字说明生成完毕，不要再调用工具
            """;

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

    /**
     * 统一入口：根据类型生成并保存代码(流式输出)
     * @param userMessage
     * @param codeGenTypeEnum
     * @param appId
     * @param freshSession
     * @return
     */

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
            case VUE_PROJECT ->
                    processVueProjectStreamWithContinuation(appId, userMessage, freshSession);
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
        boolean incremental = CodegenOutputPaths.hasExistingOutput(appId, codeGenType);
        State displayState = CodegenIncrementalStreamFilter.newState();
        if (incremental && WorkflowExecutionHolder.isActive(appId)) {
            WorkflowExecutionHolder.emitStatus(appId, "正在应用修改...");
        }
        return result
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (StrUtil.isBlank(chunk)) {
                        return;
                    }
                    synchronized (codeBuilder) {
                        codeBuilder.append(chunk);
                    }
                    String displayChunk = CodegenIncrementalStreamFilter.filterChunk(chunk, incremental, displayState);
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
                        if (incremental && WorkflowExecutionHolder.isActive(appId)) {
                            WorkflowExecutionHolder.emitStatus(appId, "已更新 index.html");
                        }
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
        boolean incremental = CodegenOutputPaths.hasExistingOutput(appId, CodeGenTypeEnum.MULTI_FILE);
        State displayState = CodegenIncrementalStreamFilter.newState();
        if (incremental && WorkflowExecutionHolder.isActive(appId)) {
            WorkflowExecutionHolder.emitStatus(appId, "正在应用修改...");
        }
        return Flux.concat(
                        runMultiFilePhase(Phase.HTML, service, userMessage, appId, accumulated, incremental, displayState),
                        runMultiFilePhase(Phase.CSS, service, userMessage, appId, accumulated, incremental, displayState),
                        runMultiFilePhase(Phase.JS, service, userMessage, appId, accumulated, incremental, displayState),
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
                                            MultiFileCodeResult accumulated,
                                            boolean incremental,
                                            State displayState) {
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
                phaseStream.doOnNext(chunk -> appendPhaseChunk(chunk, phaseBuilder, appId, incremental, displayState)),
                Flux.defer(() -> {
                    MultiFilePhasedStreamSupport.applyPhaseResult(phase, phaseBuilder.toString(), accumulated);
                    log.info("多文件阶段完成: {}, appId={}, 阶段长度={}", phase.label(), appId, phaseBuilder.length());
                    if (incremental && WorkflowExecutionHolder.isActive(appId)) {
                        WorkflowExecutionHolder.emitStatus(appId, "已更新 " + phase.fileName());
                    }
                    return Flux.empty();
                })
        );
    }

    private void appendPhaseChunk(String chunk, StringBuilder phaseBuilder, Long appId,
                                  boolean incremental, State displayState) {
        if (StrUtil.isBlank(chunk)) {
            return;
        }
        phaseBuilder.append(chunk);
        String displayChunk = CodegenIncrementalStreamFilter.filterChunk(chunk, incremental, displayState);
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
     * Vue 项目流式生成：单轮工具达上限时自动续跑，保留同一会话记忆。
     */
    private Flux<String> processVueProjectStreamWithContinuation(Long appId, String userMessage, boolean initialFreshSession) {
        AtomicInteger round = new AtomicInteger(0);
        return runVueProjectRound(appId, userMessage, initialFreshSession, round);
    }

    private Flux<String> runVueProjectRound(Long appId, String message, boolean freshSession, AtomicInteger round) {
        AtomicBoolean toolLimitExceeded = new AtomicBoolean(false);
        AiCodeGeneratorService service = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(
                appId, CodeGenTypeEnum.VUE_PROJECT, freshSession);
        TokenStream tokenStream = service.generateVueProjectCodeStream(appId, message);
        int currentRound = round.get() + 1;
        round.incrementAndGet();

        return processTokenStream(tokenStream, appId, toolLimitExceeded)
                .concatWith(Flux.defer(() -> {
                    if (!toolLimitExceeded.get()) {
                        return Flux.empty();
                    }
                    if (round.get() >= MAX_VUE_CONTINUATION_ROUNDS) {
                        log.warn("Vue 单轮工具达上限且续跑轮次已用尽, appId={}", appId);
                        if (WorkflowExecutionHolder.isActive(appId)) {
                            WorkflowExecutionHolder.emitStatus(appId,
                                    "工具调用次数较多，部分文件可能尚未完成，请查看预览或继续对话补充");
                        }
                        return Flux.empty();
                    }
                    log.info("Vue 单轮工具达上限，开始第 {} 轮续跑, appId={}", currentRound + 1, appId);
                    if (WorkflowExecutionHolder.isActive(appId)) {
                        WorkflowExecutionHolder.emitStatus(appId,
                                String.format("单轮工具调用已达上限，正在继续生成（第 %d 轮）...", currentRound + 1));
                    }
                    return runVueProjectRound(appId, VUE_CONTINUATION_USER_MESSAGE, false, round);
                }));
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId, AtomicBoolean toolLimitExceeded) {
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
                    .onError(error -> handleTokenStreamError(finished, sink, appId, error, toolLimitExceeded))
                    .start();
        }).onErrorResume(error -> {
            if (LangChain4jStreamUtils.isBenignNullResponseError(error)) {
                log.warn("Vue 流式生成收尾异常，已忽略并继续: {}", error.getMessage());
                return Flux.empty();
            }
            if (LangChain4jStreamUtils.isToolLimitExceededError(error)) {
                log.warn("Vue 流式生成工具达上限，将尝试续跑: {}", error.getMessage());
                if (toolLimitExceeded != null) {
                    toolLimitExceeded.set(true);
                }
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

    private void handleTokenStreamError(AtomicBoolean finished, FluxSink<String> sink, Long appId,
                                        Throwable error, AtomicBoolean toolLimitExceeded) {
        if (LangChain4jStreamUtils.isBenignNullResponseError(error)) {
            log.warn("Vue Agent 流式结束异常（{}），工具调用可能已完成，按成功收尾处理", error.getMessage());
            finishTokenStream(finished, sink, appId);
            return;
        }
        if (LangChain4jStreamUtils.isToolLimitExceededError(error)) {
            log.warn("Vue Agent 单轮工具调用达上限（{}），已写入文件将保留，准备续跑", error.getMessage());
            if (toolLimitExceeded != null) {
                toolLimitExceeded.set(true);
            }
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
