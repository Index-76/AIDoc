package com.project.aidoc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.aidoc.common.dto.SseMessage;
import com.project.aidoc.common.enums.SseEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * SSE服务
 */
@Slf4j
@Service
public class SseService {

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建SSE连接
     */
    public SseEmitter createConnection(String sessionId) {
        // 移除旧的连接
        SseEmitter oldEmitter = emitters.remove(sessionId);
        if (oldEmitter != null) {
            oldEmitter.complete();
        }

        // 创建新的SSE emitter
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30)); // 30分钟超时

        emitters.put(sessionId, emitter);

        // 设置回调
        emitter.onCompletion(() -> {
            log.info("SSE Connect Complete: {}", sessionId);
            emitters.remove(sessionId);
        });

        emitter.onTimeout(() -> {
            log.info("SSE Connect Timeout: {}", sessionId);
            emitters.remove(sessionId);
            emitter.complete();
        });

        emitter.onError((ex) -> {
            log.error("SSE Connect Error: {}", sessionId, ex);
            emitters.remove(sessionId);
            emitter.complete();
        });

        log.info("SSE New Connect: {}", sessionId);
        return emitter;
    }

    /**
     * 发送工具开始执行消息
     */
    public void sendToolBeginMessage(String sessionId, int toolCode) {
        sendMessage(sessionId, SseEventType.TOOL_BEGIN.getEventType(), toolCode + "b");
    }

    /**
     * 发送AI回复完成消息
     */
    public void sendAiReplyFinishMessage(String sessionId, String status) {
        sendMessage(sessionId, SseEventType.AI_REPLY_FINISH.getEventType(), status);
    }

    /**
     * 发送错误消息
     */
    public void sendErrorMessage(String sessionId, String errorMessage) {
        sendMessage(sessionId, SseEventType.ERROR.getEventType(), errorMessage);
    }

    /**
     * 发送通用消息
     */
    private void sendMessage(String sessionId, String eventType, Object data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            log.warn("SSE Connect Not Found: {}", sessionId);
            return;
        }

        try {
            SseMessage sseMessage = new SseMessage(eventType, sessionId, data);
            String jsonData = objectMapper.writeValueAsString(sseMessage);

            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(jsonData));

            log.info("SSE Message Sent Success - Session: {}, Event: {}, Data: {}", sessionId, eventType, data);

        } catch (IOException e) {
            log.error("SSE Message Send Error - Session: {}", sessionId, e);
            emitters.remove(sessionId);
            emitter.completeWithError(e);
        }
    }

    /**
     * 关闭连接
     */
    public void closeConnection(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
            log.info("SSE Connection Closed: {}", sessionId);
        }
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return emitters.size();
    }
}