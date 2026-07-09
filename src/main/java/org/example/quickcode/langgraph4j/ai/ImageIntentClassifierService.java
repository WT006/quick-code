package org.example.quickcode.langgraph4j.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.example.quickcode.langgraph4j.model.ImageIntentResult;

/**
 * 增量修改场景：判断用户是否明确要求更换/新增图片。
 */
public interface ImageIntentClassifierService {

    @SystemMessage(fromResource = "prompt/image-intent-classifier-system-prompt.txt")
    ImageIntentResult classify(@UserMessage String userPrompt);
}
