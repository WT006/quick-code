package org.example.quickcode.langgraph4j.node;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.example.quickcode.ai.AiCodeGenTypeRoutingService;
import org.example.quickcode.utils.SpringContextUtil;
import org.example.quickcode.langgraph4j.state.WorkflowContext;
import org.example.quickcode.model.enums.CodeGenTypeEnum;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class RouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum generationType = context.getGenerationType();
            if (generationType != null) {
                log.info("使用应用预设的代码生成类型: {} ({})", generationType.getValue(), generationType.getText());
            } else {
                try {
                    AiCodeGenTypeRoutingService routingService = SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class);
                    generationType = routingService.routeCodeGenType(context.getOriginalPrompt());
                    log.info("AI智能路由完成，选择类型: {} ({})", generationType.getValue(), generationType.getText());
                } catch (Exception e) {
                    log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                    generationType = CodeGenTypeEnum.HTML;
                }
                context.setGenerationType(generationType);
            }

            context.setCurrentStep("智能路由");
            return WorkflowContext.saveContext(context);
        });
    }
}
