package org.example.quickcode.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.example.quickcode.model.dto.chatHistory.ChatHistoryQueryRequest;
import org.example.quickcode.model.entity.ChatHistory;
import org.example.quickcode.model.entity.User;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 */
public interface ChatHistoryService extends IService<ChatHistory> {
    /**
     * 添加对话消息
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 将数据库保存的对话加载到chatMemory中
     * @param appId
     * @param chatMemory
     * @param maxCount
     * @return
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 删除对话记忆
      * @param appId
     * @return
     */
    boolean deleteByAppId(Long appId);

    /**
     * 分页游标查询
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                             LocalDateTime lastCreateTime,
                                             User loginUser);
    /**
     * 获取查询包装类
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

}
