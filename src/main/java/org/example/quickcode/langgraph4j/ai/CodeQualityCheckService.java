package org.example.quickcode.langgraph4j.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.example.quickcode.langgraph4j.model.QualityResult;


public interface CodeQualityCheckService {

    /**
     * 检查代码质量
     * AI 会分析代码并返回质量检查结果
     */
    @SystemMessage(fromResource = "prompt/code-quality-check-system-prompt.txt")
    @UserMessage("""
            请检查以下项目代码的质量：

            {{codeContent}}
            """)
    QualityResult checkCodeQuality(@V("codeContent") String codeContent);
}
