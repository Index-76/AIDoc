package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.entity.ChatMessage;
import com.project.aidoc.service.ChatService;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @GetMapping("/history")
    public Result<List<ChatMessage>> getChatHistory() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        List<ChatMessage> history = chatService.getChatHistoryByUserId(userId);
        return Result.success(history);
    }

    @GetMapping("/history/session/{sessionId}")
    public Result<List<ChatMessage>> getChatHistoryBySession(@PathVariable("sessionId") String sessionId) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        List<ChatMessage> history = chatService.getChatHistoryByUserIdAndSession(userId, sessionId);
        return Result.success(history);
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