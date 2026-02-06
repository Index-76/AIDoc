package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.entity.ChatMessage;
import com.project.aidoc.service.ChatService;
import com.project.aidoc.service.impl.ChatServiceImpl;
import cn.dev33.satoken.stp.StpUtil;
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

    @GetMapping("/sessions")
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
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default_session"; // 使用请求中的会话ID或默认会话ID
        String response = chatService.processMessage(userId, sessionId, request.getMessage());

        return Result.success(response);
    }

    @PostMapping("/new")
    public Result<String> createNewChat() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        // 生成新的会话ID
        String newSessionId = UUID.randomUUID().toString();
        return Result.success(newSessionId);
    }

    // 内部类用于接收发送消息的请求体
    public static class MessageRequest {
        private String message;
        private String sessionId;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}