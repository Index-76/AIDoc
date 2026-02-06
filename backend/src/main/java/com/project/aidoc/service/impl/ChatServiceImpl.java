package com.project.aidoc.service.impl;

import com.project.aidoc.entity.ChatMessage;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.repository.ChatMessageRepository;
import com.project.aidoc.service.ChatService;
import com.project.aidoc.service.UserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserConfigService userConfigService;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<ChatMessage> getChatHistoryByUserId(Long userId) {
        return chatMessageRepository.findByUserIdOrderByTimestampAsc(userId);
    }

    @Override
    public List<ChatMessage> getChatHistoryByUserIdAndSession(Long userId, String sessionId) {
        return chatMessageRepository.findByUserIdAndSessionIdOrderByTimestampAsc(userId, sessionId);
    }

    @Override
    public ChatMessage saveMessage(Long userId, String sessionId, String content, String senderType) {
        ChatMessage message = new ChatMessage();
        message.setUserId(userId);
        message.setSessionId(sessionId);
        message.setContent(content);
        message.setSenderType(senderType);
        return chatMessageRepository.save(message);
    }

    @Override
    public void clearChatHistoryByUserId(Long userId) {
        chatMessageRepository.deleteByUserId(userId);
    }

    @Override
    public String processMessage(Long userId, String sessionId, String message) {
        // 保存用户消息
        saveMessage(userId, sessionId, message, "USER");

        // 使用真实AI服务获取响应，传递sessionId以获取对话历史
        String aiResponse = callAIService(userId, sessionId, message);

        // 保存AI消息
        saveMessage(userId, sessionId, aiResponse, "AI");

        return aiResponse;
    }

    @Override
    public Set<String> getAllSessionIdsByUserId(Long userId) {
        // 从数据库中获取用户的所有聊天消息
        List<ChatMessage> allMessages = chatMessageRepository.findByUserIdOrderByTimestampAsc(userId);

        // 提取唯一的会话ID
        Set<String> sessionIds = allMessages.stream()
                .map(ChatMessage::getSessionId)
                .filter(sessionId -> sessionId != null && !sessionId.isEmpty())
                .collect(Collectors.toSet());

        return sessionIds;
    }

    @Override
    public void deleteSessionBySessionId(Long userId, String sessionId) {
        // 检查会话是否存在
        List<ChatMessage> sessionMessages = chatMessageRepository.findByUserIdAndSessionIdOrderByTimestampAsc(userId,
                sessionId);

        if (sessionMessages.isEmpty()) {
            throw new IllegalArgumentException("会话不存在: " + sessionId);
        }

        // 会话存在，执行删除操作
        chatMessageRepository.deleteByUserIdAndSessionId(userId, sessionId);
    }

    private String callAIService(Long userId, String sessionId, String message) {
        // 获取用户的配置
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null || userConfig.getSiliconFlowApiKey() == null ||
                userConfig.getSiliconFlowApiKey().isEmpty()) {
            // 如果没有配置API密钥，返回提示信息
            return "请先配置硅基流动API密钥";
        }

        try {
            // 准备请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + userConfig.getSiliconFlowApiKey());

            // 构建消息历史
            List<Map<String, String>> messages = new ArrayList<>();

            // 系统提示
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个智能文档助手。你拥有调用目录查看、内容总结、格式转换、智能填表、智能修改工具的能力。请仔细记住并理解用户的所有对话历史，基于之前的对话内容来回答当前问题。你的回答应该体现出对之前对话的理解和连贯性。");
            messages.add(systemMsg);

            // 获取当前会话的历史记录（限制最近的15条消息以避免超出token限制）
            List<ChatMessage> history = chatMessageRepository.findByUserIdAndSessionIdOrderByTimestampAsc(userId,
                    sessionId);

            // 如果历史记录很多，只取最近的15条
            if (history.size() > 15) {
                history = history.subList(Math.max(0, history.size() - 15), history.size());
            }

            // 添加历史对话记录到消息中（排除当前这条用户消息）
            for (ChatMessage chatMessage : history) {
                Map<String, String> historyMsg = new HashMap<>();
                if ("USER".equals(chatMessage.getSenderType())) {
                    historyMsg.put("role", "user");
                    historyMsg.put("content", chatMessage.getContent());
                    messages.add(historyMsg);
                } else if ("AI".equals(chatMessage.getSenderType())) {
                    historyMsg.put("role", "assistant");
                    historyMsg.put("content", chatMessage.getContent());
                    messages.add(historyMsg);
                }
            }

            // 添加当前用户消息
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", message);
            messages.add(userMsg);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", userConfig.getChatModelName());
            requestBody.put("messages", messages);
            requestBody.put("stream", false);

            // 创建HttpEntity
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // 发送请求
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                    userConfig.getSiliconFlowBaseUrl(),
                    requestEntity,
                    String.class);

            if (responseEntity.getStatusCode().value() == 200) {
                String responseBody = responseEntity.getBody();
                JsonNode responseJson = objectMapper.readTree(responseBody);

                // 解析响应，获取AI的回答
                JsonNode choicesNode = responseJson.get("choices");
                if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                    JsonNode firstChoice = choicesNode.get(0);
                    JsonNode messageNode = firstChoice.get("message");
                    if (messageNode != null) {
                        String aiResponse = messageNode.get("content").asText();
                        return aiResponse;
                    }
                }
            } else {
                return "API请求失败，状态码: " + responseEntity.getStatusCodeValue();
            }
        } catch (Exception e) {
            return "调用AI服务时发生错误: " + e.getMessage();
        }

        return "未能获取AI响应";
    }
}