package com.project.aidoc.service.impl;

import com.project.aidoc.entity.ChatMessage;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.repository.ChatMessageRepository;
import com.project.aidoc.service.ChatService;
import com.project.aidoc.service.UserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public ChatServiceImpl() {
        this.restTemplate = new RestTemplate();
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
        message.setTimestamp(LocalDateTime.now());
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

        // 使用AI服务获取响应，传递sessionId以获取对话历史
        String aiResponse = callAIService(userId, sessionId, message);

        // 保存AI消息
        saveMessage(userId, sessionId, aiResponse, "AI");

        return aiResponse;
    }

    @Override
    @Async
    public void processMessageAsync(Long userId, String sessionId, String message) {
        try {
            // 保存用户消息
            saveMessage(userId, sessionId, message, "USER");

            // 使用AI服务获取响应
            String aiResponse = callAIService(userId, sessionId, message);

            // 保存AI消息
            saveMessage(userId, sessionId, aiResponse, "AI");
        } catch (Exception e) {
            // 记录异步处理错误，但不影响主流程
            e.printStackTrace();
        }
    }

    @Override
    public void createWelcomeMessage(Long userId, String sessionId) {
        // 创建欢迎消息
        ChatMessage welcomeMessage = new ChatMessage();
        welcomeMessage.setUserId(userId);
        welcomeMessage.setSessionId(sessionId);
        welcomeMessage.setContent("您好！我是您的AI智能文档助手，有什么我可以帮您的吗？");
        welcomeMessage.setSenderType("AI");
        welcomeMessage.setTimestamp(LocalDateTime.now());
        chatMessageRepository.save(welcomeMessage);
    }

    @Override
    public Set<String> getAllSessionIdsByUserId(Long userId) {

        List<ChatMessage> allMessages = chatMessageRepository.findByUserIdOrderByTimestampAsc(userId);

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
            systemMsg.put("content", "你是AI助手，专门帮助用户管理文档和解答相关问题。你可以使用以下工具：\n" +
                    "1. 目录查看 - 查看文件目录结构\n" +
                    "2. 内容总结 - 总结文档内容\n" +
                    "3. 格式转换 - 转换文件格式\n" +
                    "4. 智能填表 - 自动填写表格\n" +
                    "5. 智能修改 - 智能编辑文档\n" +
                    "当用户需要使用这些功能时，请在回复中明确提及相应的工具名称。");
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
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.7);

            // 发送请求到硅基流动API
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    userConfig.getSiliconFlowBaseUrl(),
                    requestEntity,
                    Map.class);

            // 解析响应
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                    return (String) messageObj.get("content");
                }
            }

            return "抱歉，我无法处理您的请求。";
        } catch (Exception e) {
            e.printStackTrace();
            return "抱歉，处理您的请求时出现错误：" + e.getMessage();
        }
    }
}