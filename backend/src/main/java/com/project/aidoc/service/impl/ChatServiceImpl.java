package com.project.aidoc.service.impl;

import com.project.aidoc.entity.ChatMessage;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.repository.ChatMessageRepository;
import com.project.aidoc.service.ChatService;
import com.project.aidoc.service.UserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserConfigService userConfigService;

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

        // 使用真实AI服务获取响应
        String aiResponse = callAIService(userId, message);

        // 保存AI消息
        saveMessage(userId, sessionId, aiResponse, "AI");

        return aiResponse;
    }

    private String callAIService(Long userId, String message) {
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
            systemMsg.put("content", "你是AI助手，专门帮助用户管理文档和解答相关问题。");
            messages.add(systemMsg);

            // 用户消息
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