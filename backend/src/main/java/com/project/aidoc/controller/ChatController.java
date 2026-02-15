package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.entity.ChatMessage;
import com.project.aidoc.service.ChatService;
import com.project.aidoc.service.impl.ChatServiceImpl;
import cn.dev33.satoken.stp.StpUtil;
import lombok.Data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @GetMapping("/sessionList")
    public Result<Set<String>> getSessionList() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        Set<String> sessionIds = chatService.getAllSessionIdsByUserId(userId);
        return Result.success(sessionIds);
    }

    @GetMapping("/session/{sessionId}/history")
    public Result<List<ChatMessage>> getChatHistoryBySession(@PathVariable("sessionId") String sessionId) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());

        // 检查会话是否存在
        List<ChatMessage> history = chatService.getChatHistoryByUserIdAndSession(userId, sessionId);
        if (history.isEmpty()) {
            return Result.error(400, "会话不存在: " + sessionId);
        }

        return Result.success(history);
    }

    @DeleteMapping("/session/{sessionId}/delete")
    public Result<String> deleteSession(@PathVariable("sessionId") String sessionId) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());

        try {
            chatService.deleteSessionBySessionId(userId, sessionId);
            return Result.success("会话删除成功");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/message")
    public Result<String> sendMessage(@RequestBody MessageRequest request) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default_session";

        try {
            // 异步处理消息，立即返回成功
            chatService.processMessageAsync(userId, sessionId, request.getMessage());
            return Result.success("消息发送成功");
        } catch (Exception e) {
            return Result.error(500, "消息发送失败: " + e.getMessage());
        }
    }

    @PostMapping("/new")
    public Result<String> createNewChat() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        try {
            // 生成新的会话ID
            String newSessionId = UUID.randomUUID().toString();

            // 创建新会话时自动添加欢迎消息
            Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
            chatService.createWelcomeMessage(userId, newSessionId);

            return Result.success(newSessionId);
        } catch (Exception e) {
            return Result.error(500, "创建新对话失败: " + e.getMessage());
        }
    }

    // 内部类用于接收发送消息的请求体
    @Data
    public static class MessageRequest {
        private String message;
        private String sessionId;
    }
}