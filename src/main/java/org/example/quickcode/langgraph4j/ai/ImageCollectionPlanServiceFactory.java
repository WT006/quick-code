package org.example.quickcode.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.example.quickcode.utils.SpringContextUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImageCollectionPlanServiceFactory {

    /**
     * 每次调用创建新实例，配合 prototype ChatModel 支持并发。
     */
    public ImageCollectionPlanService createImageCollectionPlanService() {
        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public ImageCollectionPlanService imageCollectionPlanService() {
        return createImageCollectionPlanService();
    }
}
