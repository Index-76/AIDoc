package com.project.aidoc.service;

import com.project.aidoc.common.dto.AiDecisionResult;
import com.project.aidoc.common.enums.ToolType;
import com.project.aidoc.config.SystemConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI决策服务
 */
@Slf4j
@Service
public class AiDecisionService {

    @Autowired
    private ApiService apiService;

    @Autowired
    private SystemConfigManager systemConfigManager;

    /**
     * 执行AI决策
     * 
     * @param userId      用户ID
     * @param userMessage 用户消息
     * @param sessionId   会话ID
     * @return 决策结果
     */
    public AiDecisionResult makeDecision(String userId, String userMessage, String sessionId) {
        log.info("开始 AI 决策，用户 ID: {}, 会话 ID: {}, 用户消息：{}", userId, sessionId, userMessage);
        // 控制是否执行预处理逻辑
        boolean needPreprocess = true;
        int toolCode = 0;
        String reason = "默认决策";

        try {
            // 根据 needPreprocess 变量决定是否执行预处理
            if (needPreprocess) {
                // 执行预处理逻辑
                log.info("执行预处理逻辑");
                // 步骤 1: 检查是否有<tools=?>格式的标记
                Pattern toolsPattern = Pattern.compile("<tools=(\\d+)>");
                Matcher matcher = toolsPattern.matcher(userMessage);

                if (matcher.find()) {
                    // 发现工具标记，直接使用指定工具
                    toolCode = Integer.parseInt(matcher.group(1));
                    reason = "发现工具标记：" + toolCode;
                    log.info("发现工具标记，直接使用工具: {}", toolCode);
                    // 移除所有工具标记
                    String cleanedMessage = userMessage.replaceAll("<tools=\\d+>", "").trim();

                    return new AiDecisionResult(toolCode, true, reason, cleanedMessage);
                }

                // 步骤 2: 检查疑问词和否定词 - 如果有，则跳过工具关键词检测，直接进 AI决策
                if (containsQuestionOrNegation(userMessage)) {
                    // 不直接返回，继续到 AI决策
                } else {
                    // 步骤 3: 没有疑问/否定词时，才检测工具关键词
                    ToolType toolType = ToolType.fromKeyword(userMessage);
                    if (toolType != ToolType.NONE) {
                        toolCode = toolType.getCode();
                        reason = "检测到工具关键词：" + toolType.getDescription();
                        return new AiDecisionResult(toolCode, true, reason, userMessage);
                    } else {
                    }
                }

                // 步骤 4: 有疑问/否定词，或没有匹配到工具关键词时，调用决策 AI
            }

            // 调用决策 AI
            toolCode = callDecisionAi(userId, userMessage, sessionId);
            reason = "AI 智能决策结果：" + toolCode;
            return new AiDecisionResult(toolCode, needPreprocess, reason, userMessage);

        } catch (Exception e) {
            e.printStackTrace();
            return new AiDecisionResult(0, false, "决策过程出错：" + e.getMessage(), userMessage);
        }
    }

    /**
     * 检查消息是否包含疑问词或否定词
     */
    private boolean containsQuestionOrNegation(String message) {

        if (message == null || message.isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase().trim();

        // 先检查是否包含工具相关关键词，如果有则不视为疑问句
        if (lowerMessage.contains("查看目录") ||
                lowerMessage.contains("目录查看") ||
                lowerMessage.contains("查看文件") ||
                lowerMessage.contains("文件列表")) {
            return false;
        }

        // 疑问词
        String[] questionPatterns = {
                "\\?$", // 以？结尾
                "什么\\s*$", // 以"什么"结尾（后面可能有空格）
                "怎么\\s*$", // 以"怎么"结尾
                "为什么\\s*$", // 以"为什么"结尾
                "\\?\\s*$" // 以？结尾
        };

        for (String pattern : questionPatterns) {
            if (lowerMessage.matches(".*" + pattern)) {
                return true;
            }
        }

        // 否定词
        String[] negationWords = { "不", "别", "不要", "不用", "没有", "无", "非" };

        for (String word : negationWords) {
            if (lowerMessage.contains(word)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 调用决策AI
     */
    private int callDecisionAi(String userId, String userMessage, String sessionId) {
        try {
            // 获取决策AI 配置
            String decisionAiApi = systemConfigManager.getDecisionAiApi(userId);
            String decisionAiKey = systemConfigManager.getDecisionAiKey(userId);
            String decisionAiModel = systemConfigManager.getDecisionAiModel(userId); // 使用用户配置的模型

            if (decisionAiApi == null || decisionAiApi.isEmpty()) {
                log.warn("Decision AI configuration missing, using default decision");
                return 0;
            }

            if (decisionAiKey == null || decisionAiKey.isEmpty()) {
                log.warn("User {} has not configured API key, using default decision", userId);
                return 0;
            }

            // 构造决策提示词
            String prompt = buildDecisionPrompt(userMessage);

            // 调用决策AI API
            String response = apiService.callExternalApi(
                    decisionAiApi,
                    decisionAiKey,
                    prompt,
                    decisionAiModel);

            // 解析决策结果
            return parseDecisionResponse(response);

        } catch (Exception e) {
            log.error("调用决策AI失败", e);
            return 0; // 默认不使用工具
        }
    }

    /**
     * 构造决策提示词
     */
    private String buildDecisionPrompt(String userMessage) {
        return String.format("""
                请分析以下用户请求，并决定是否需要使用工具以及使用哪个工具。

                用户请求：%s

                工具选项：
                0 - 不使用工具（直接对话）
                1 - 目录查看工具
                2 - 内容总结工具
                3 - 格式转换工具
                4 - 智能填表工具
                5 - 智能修改工具

                请只回复数字0-5，表示你的决策。
                """, userMessage);
    }

    /**
     * 解析决策AI响应
     */
    private int parseDecisionResponse(String response) {
        if (response == null || response.isEmpty()) {
            return 0;
        }

        try {
            // 提取第一个数字
            Pattern numberPattern = Pattern.compile("\\d+");
            Matcher matcher = numberPattern.matcher(response.trim());

            if (matcher.find()) {
                int toolCode = Integer.parseInt(matcher.group());
                if (toolCode >= 0 && toolCode <= 5) {
                    return toolCode;
                }
            }
        } catch (Exception e) {
            log.error("解析决策响应失败: {}", response, e);
        }

        return 0; // 默认不使用工具
    }
}