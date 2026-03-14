package com.project.aidoc.service;

import com.project.aidoc.entity.ChatMessage;
import java.util.List;
import java.util.Set;

public interface ChatService {
    List<ChatMessage> getChatHistoryByUserId(String userId);

    List<ChatMessage> getChatHistoryByUserIdAndSession(String userId, String sessionId);

    ChatMessage saveMessage(String userId, String sessionId, String content, String senderType);

    void clearChatHistoryByUserId(String userId);

    String processMessage(String userId, String sessionId, String message);

    Set<String> getAllSessionIdsByUserId(String userId);

    void deleteSessionBySessionId(String userId, String sessionId);

    // 异步处理消息的方法
    void processMessageAsync(String userId, String sessionId, String message);

    // 创建欢迎消息的方法
    void createWelcomeMessage(String userId, String sessionId);
}
