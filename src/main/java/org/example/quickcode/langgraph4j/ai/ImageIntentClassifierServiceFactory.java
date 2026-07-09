package org.example.quickcode.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImageIntentClassifierServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Bean
    public ImageIntentClassifierService createImageIntentClassifierService() {
        return AiServices.builder(ImageIntentClassifierService.class)
                .chatModel(chatModel)
                .build();
    }
}
