package com.project.aidoc.service.impl;

import com.project.aidoc.common.dto.AiDecisionResult;
import com.project.aidoc.entity.ChatMessage;
import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.repository.ChatMessageRepository;
import com.project.aidoc.service.*;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private UserConfigService userConfigService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private AiDecisionService aiDecisionService;

    @Autowired
    private ToolExecutionService toolExecutionService;

    @Autowired
    private SseService sseService;

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
            // 保存用户消息到数据库
            ChatMessage userMessage = saveMessage(userId, sessionId, message, "USER");
            
            // 执行AI决策
            AiDecisionResult decisionResult = aiDecisionService.makeDecision(message, sessionId);
            int toolCode = decisionResult.getToolCode();
            
            // 发送SSE通知：工具开始执行（只发送一次，根据实际工具代码）
            if (toolCode > 0) {
                // 需要调用工具，发送具体的工具代码
                sseService.sendToolBeginMessage(sessionId, toolCode);
            } else {
                // 不需要调用工具，发送0表示AI正在思考
                sseService.sendToolBeginMessage(sessionId, 0);
            }

            String toolResult = "";
            // 如果需要使用工具，则执行工具
            if (toolCode > 0 && toolExecutionService.isToolAvailable(toolCode)) {
                toolResult = toolExecutionService.executeTool(userId, toolCode, message, sessionId);
                
                // 对于目录查看工具，直接返回结果而不经过AI处理
                if (toolCode == 1) { // DIRECTORY_VIEW
                    // 保存工具结果作为AI消息
                    saveMessage(userId, sessionId, toolResult, "AI");
                    
                    // 发送SSE通知：AI回复完成
                    sseService.sendAiReplyFinishMessage(sessionId, "success");
                    return;
                }
            }

            // 将工具结果和原始用户消息一起交给对话AI
            String aiResponse = callAIServiceWithTools(userId, sessionId, message, toolResult);

            // 保存AI消息
            saveMessage(userId, sessionId, aiResponse, "AI");
            
            // 发送SSE通知：AI回复完成
            sseService.sendAiReplyFinishMessage(sessionId, "success");

        } catch (Exception e) {
            // 记录异步处理错误
            e.printStackTrace();
            // 发送错误通知
            sseService.sendErrorMessage(sessionId, "处理消息时发生错误: " + e.getMessage());
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
        // 获取用户的所有消息，按时间升序排列
        List<ChatMessage> allMessages = chatMessageRepository.findByUserIdOrderByTimestampAsc(userId);

        // 使用 LinkedHashMap 保持插入顺序，按session首次出现的时间排序
        Map<String, LocalDateTime> sessionFirstSeen = new LinkedHashMap<>();
        
        for (ChatMessage message : allMessages) {
            String sessionId = message.getSessionId();
            if (sessionId != null && !sessionId.isEmpty()) {
                // 如果session第一次出现，记录其首次出现时间
                if (!sessionFirstSeen.containsKey(sessionId)) {
                    sessionFirstSeen.put(sessionId, message.getTimestamp());
                }
            }
        }

        // 按首次出现时间排序（升序：先创建的在前）
        return sessionFirstSeen.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
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

    /**
     * 调用AI服务（带工具结果）
     */
    private String callAIServiceWithTools(Long userId, String sessionId, String userMessage, String toolResult) {
        // 获取用户的配置
        UserConfig userConfig = userConfigService.getUserConfig(userId);
        if (userConfig == null || userConfig.getSiliconFlowApiKey() == null ||
                userConfig.getSiliconFlowApiKey().isEmpty()) {
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
                    "当用户需要使用这些功能时，请严格按照工具执行结果的格式进行回复，不要重新组织语言。\n" +
                    "特别是目录查看工具的结果已经是标准格式，请直接使用，不要做任何修改或总结。");
            messages.add(systemMsg);

            // 获取当前会话的历史记录（限制最近的15条消息）
            List<ChatMessage> history = chatMessageRepository.findByUserIdAndSessionIdOrderByTimestampAsc(userId,
                    sessionId);

            // 如果历史记录很多，只取最近的15条
            if (history.size() > 15) {
                history = history.subList(Math.max(0, history.size() - 15), history.size());
            }

            // 添加历史对话记录到消息中
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

            // 添加当前用户消息和工具结果
            StringBuilder combinedMessage = new StringBuilder();
            combinedMessage.append("用户请求: ").append(userMessage);
            
            if (toolResult != null && !toolResult.isEmpty()) {
                combinedMessage.append("\n\n工具执行结果: ").append(toolResult);
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", combinedMessage.toString());
            messages.add(userMsg);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", userConfig.getChatModelName());
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.7);

            // 发送请求到硅基流动API
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

    /**
     * 原有的AI服务调用方法（保持兼容性）
     */
    private String callAIService(Long userId, String sessionId, String message) {
        return callAIServiceWithTools(userId, sessionId, message, "");
    }
}