package org.example.quickcode.langgraph4j.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.example.quickcode.langgraph4j.WorkflowExecutionHolder;
import org.example.quickcode.utils.SpringContextUtil;
import org.example.quickcode.langgraph4j.ai.CodeQualityCheckService;
import org.example.quickcode.langgraph4j.ai.CodeQualityCheckServiceFactory;
import org.example.quickcode.langgraph4j.model.QualityResult;
import org.example.quickcode.langgraph4j.state.WorkflowContext;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码质量检查节点
 */
@Slf4j
public class CodeQualityCheckNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            Long appId = context.getAppId();
            log.info("执行节点: 代码质量检查");
            WorkflowExecutionHolder.emitStatus(appId, "正在进行代码审查");
            String generatedCodeDir = context.getGeneratedCodeDir();
            QualityResult qualityResult;
            try {
                // 1. 读取并拼接代码文件内容
                String codeContent = readAndConcatenateCodeFiles(generatedCodeDir);
                if (StrUtil.isBlank(codeContent)) {
                    log.warn("未找到可检查的代码文件");
                    qualityResult = QualityResult.builder()
                            .isValid(false)
                            .errors(List.of("未找到可检查的代码文件"))
                            .suggestions(List.of("请确保代码生成成功"))
                            .build();
                } else {
                    // 2. 调用 AI 进行代码质量检查
                    CodeQualityCheckServiceFactory factory = SpringContextUtil.getBean(CodeQualityCheckServiceFactory.class);
                    CodeQualityCheckService qualityCheckService = factory.createCodeQualityCheckService();
                    qualityResult = qualityCheckService.checkCodeQuality(codeContent);
                    log.info("代码质量检查完成 - 是否通过: {}", qualityResult.getIsValid());
                }
            } catch (Exception e) {
                log.error("代码质量检查异常: {}", e.getMessage(), e);
                qualityResult = QualityResult.builder()
                        .isValid(true) // 异常直接跳到下一个步骤
                        .build();
            }
            // 3. 更新状态
            context.setCurrentStep("代码质量检查");
            if (!qualityResult.getIsValid()) {
                context.setQualityCheckRetryCount(context.getQualityCheckRetryCount() + 1);
            }
            context.setQualityResult(qualityResult);
            if (qualityResult.getIsValid()) {
                WorkflowExecutionHolder.emitStatus(appId, "代码审查结束");
            } else {
                WorkflowExecutionHolder.emitStatus(appId, "代码审查未通过，准备重新生成");
            }
            return WorkflowContext.saveContext(context);
        });
    }
    /**
     * 需要检查的文件扩展名
     */
    private static final List<String> CODE_EXTENSIONS = Arrays.asList(
            ".html", ".htm", ".css", ".js", ".json", ".vue", ".ts", ".jsx", ".tsx"
    );

    /** 质量检查传入 AI 的代码内容最大字符数 */
    private static final int MAX_CODE_CONTENT_LENGTH = 6000;

    /**
     * 读取并拼接代码目录下的所有代码文件，限制总长度防止超 token 限制
     */
    private static String readAndConcatenateCodeFiles(String codeDir) {
        if (StrUtil.isBlank(codeDir)) {
            return "";
        }
        File directory = new File(codeDir);
        if (!directory.exists() || !directory.isDirectory()) {
            log.error("代码目录不存在或不是目录: {}", codeDir);
            return "";
        }
        StringBuilder codeContent = new StringBuilder();
        codeContent.append("# 项目文件结构和代码内容\n\n");
        // 使用 Hutool 的 walkFiles 方法遍历所有文件，限制总长度防止超 token 限制
        FileUtil.walkFiles(directory, file -> {
            if (codeContent.length() >= MAX_CODE_CONTENT_LENGTH) {
                return;
            }
            // 过滤条件：跳过隐藏文件、特定目录下的文件、非代码文件
            if (shouldSkipFile(file, directory)) {
                return;
            }
            if (isCodeFile(file)) {
                String relativePath = FileUtil.subPath(directory.getAbsolutePath(), file.getAbsolutePath());
                String header = "## 文件: " + relativePath + "\n\n";
                if (codeContent.length() + header.length() > MAX_CODE_CONTENT_LENGTH) {
                    return;
                }
                codeContent.append(header);
                String fileContent = FileUtil.readUtf8String(file);
                String appendContent = fileContent + "\n\n";
                // 如果当前文件内容会超限，只截取剩余部分
                if (codeContent.length() + appendContent.length() > MAX_CODE_CONTENT_LENGTH) {
                    int remaining = MAX_CODE_CONTENT_LENGTH - codeContent.length();
                    if (remaining > 50) {
                        codeContent.append(appendContent, 0, remaining);
                    }
                } else {
                    codeContent.append(appendContent);
                }
            }
        });
        String result = codeContent.toString();
        if (result.length() >= MAX_CODE_CONTENT_LENGTH) {
            log.warn("代码内容超过限制({}字符)，已截断", MAX_CODE_CONTENT_LENGTH);
        }
        return result;
    }

    /**
     * 判断是否应该跳过此文件
     */
    private static boolean shouldSkipFile(File file, File rootDir) {
        String relativePath = FileUtil.subPath(rootDir.getAbsolutePath(), file.getAbsolutePath());
        // 跳过隐藏文件
        if (file.getName().startsWith(".")) {
            return true;
        }
        // 跳过特定目录下的文件
        return relativePath.contains("node_modules" + File.separator) ||
                relativePath.contains("dist" + File.separator) ||
                relativePath.contains("target" + File.separator) ||
                relativePath.contains(".git" + File.separator);
    }

    /**
     * 判断是否是需要检查的代码文件
     */
    private static boolean isCodeFile(File file) {
        String fileName = file.getName().toLowerCase();
        return CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

}
