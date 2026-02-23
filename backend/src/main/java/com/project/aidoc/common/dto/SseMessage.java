package com.project.aidoc.common.dto;

import lombok.Data;

/**
 * SSE消息DTO
 */
@Data
public class SseMessage {
    /**
     * 事件类型
     */
    private String eventType;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 数据内容
     */
    private Object data;
    
    /**
     * 时间戳
     */
    private long timestamp;
    
    public SseMessage() {}
    
    public SseMessage(String eventType, String sessionId, Object data) {
        this.eventType = eventType;
        this.sessionId = sessionId;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
}