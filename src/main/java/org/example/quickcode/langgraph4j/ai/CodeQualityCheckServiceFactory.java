package org.example.quickcode.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.example.quickcode.utils.SpringContextUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CodeQualityCheckServiceFactory {

    /**
     * 每次调用创建新实例，配合 prototype ChatModel 支持并发。
     */
    public CodeQualityCheckService createCodeQualityCheckService() {
        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        return AiServices.builder(CodeQualityCheckService.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public CodeQualityCheckService codeQualityCheckService() {
        return createCodeQualityCheckService();
    }
}
