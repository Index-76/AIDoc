package com.project.aidoc.service;

import com.project.aidoc.entity.ChatMessage;
import java.util.List;
import java.util.Set;

public interface ChatService {
    List<ChatMessage> getChatHistoryByUserId(Long userId);

    List<ChatMessage> getChatHistoryByUserIdAndSession(Long userId, String sessionId);

    ChatMessage saveMessage(Long userId, String sessionId, String content, String senderType);

    void clearChatHistoryByUserId(Long userId);

    String processMessage(Long userId, String sessionId, String message);

    Set<String> getAllSessionIdsByUserId(Long userId);

    void deleteSessionBySessionId(Long userId, String sessionId);

    // 异步处理消息的方法
    void processMessageAsync(Long userId, String sessionId, String message);

    // 创建欢迎消息的方法
    void createWelcomeMessage(Long userId, String sessionId);
}