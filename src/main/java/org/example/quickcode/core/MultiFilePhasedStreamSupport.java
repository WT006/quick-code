package org.example.quickcode.core;

import cn.hutool.core.util.StrUtil;
import org.example.quickcode.ai.model.MultiFileCodeResult;
import org.example.quickcode.core.parser.CodeBlockExtractor;
import org.example.quickcode.core.stream.StreamContentNormalizer;
import org.example.quickcode.exception.BusinessException;
import org.example.quickcode.exception.ErrorCode;

/**
 * 多文件分阶段流式生成的阶段定义与解析辅助。
 */
final class MultiFilePhasedStreamSupport {

    enum Phase {
        HTML("HTML代码生成中", "HTML"),
        CSS("CSS代码生成中", "CSS"),
        JS("JavaScript代码生成中", "JavaScript");

        private final String stepText;
        private final String label;

        Phase(String stepText, String label) {
            this.stepText = stepText;
            this.label = label;
        }

        String stepText() {
            return stepText;
        }

        String label() {
            return label;
        }

        String fileName() {
            return switch (this) {
                case HTML -> "index.html";
                case CSS -> "style.css";
                case JS -> "script.js";
            };
        }
    }

    private MultiFilePhasedStreamSupport() {
    }

    static String buildPhasePrompt(Phase phase, String userMessage) {
        String phaseInstruction = switch (phase) {
            case HTML -> """
                    
                    【阶段任务 - 多文件模式】仅生成 index.html，只输出 1 个 ```html 代码块（完整 HTML）。
                    必须在 <head> 中通过 <link rel="stylesheet" href="style.css"> 引用外部 CSS。
                    必须在 </body> 前通过 <script src="script.js"></script> 引用外部 JS。
                    禁止内联 <style> 与 <script>（样式与脚本由独立文件提供）。
                    不要输出步骤说明文字，不要输出 CSS 或 JavaScript 代码块。
                    """;
            case CSS -> """
                    
                    【阶段任务 - 多文件模式】仅生成 style.css，只输出 1 个 ```css 代码块（完整 CSS）。
                    不要输出步骤说明文字，不要输出 HTML 或 JavaScript 代码块。
                    """;
            case JS -> """
                    
                    【阶段任务 - 多文件模式】仅生成 script.js，只输出 1 个 ```javascript 代码块（完整 JS）。
                    不要输出步骤说明文字，不要输出 HTML 或 CSS 代码块。
                    代码块必须以 ```javascript 开头并以 ``` 结尾。
                    """;
        };
        return userMessage + phaseInstruction;
    }

    static void applyPhaseResult(Phase phase, String phaseContent, MultiFileCodeResult target) {
        String normalized = StreamContentNormalizer.normalize(phaseContent);
        String extracted = switch (phase) {
            case HTML -> CodeBlockExtractor.extractHtml(normalized);
            case CSS -> CodeBlockExtractor.extractCss(normalized);
            case JS -> CodeBlockExtractor.extractJs(normalized);
        };
        if (StrUtil.isBlank(extracted)) {
            throw phaseParseError(phase, normalized);
        }
        switch (phase) {
            case HTML -> target.setHtmlCode(extracted);
            case CSS -> target.setCssCode(extracted);
            case JS -> target.setJsCode(extracted);
        }
    }

    static void validateBeforeSave(MultiFileCodeResult result) {
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "多文件生成失败：HTML 代码为空");
        }
        if (StrUtil.isBlank(result.getCssCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "多文件生成失败：CSS 代码为空");
        }
        if (StrUtil.isBlank(result.getJsCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "多文件生成失败：JavaScript 代码为空");
        }
    }

    private static BusinessException phaseParseError(Phase phase, String normalized) {
        int length = normalized == null ? 0 : normalized.length();
        String preview = "";
        if (normalized != null && !normalized.isBlank()) {
            preview = normalized.substring(0, Math.min(200, normalized.length())).replace("\n", "\\n");
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR,
                String.format("多文件生成失败：未能解析 %s 代码块（阶段响应长度 %d 字，请检查 AI 是否输出了 ```%s 围栏）预览: %s",
                        phase.label(), length, phaseFenceHint(phase), preview));
    }

    private static String phaseFenceHint(Phase phase) {
        return switch (phase) {
            case HTML -> "html";
            case CSS -> "css";
            case JS -> "javascript";
        };
    }
}
