package com.project.aidoc.common.enums;

/**
 * SSE事件类型枚举
 */
public enum SseEventType {
    /**
     * 工具开始执行
     */
    TOOL_BEGIN("tool_begin"),
    
    /**
     * AI回复完成
     */
    AI_REPLY_FINISH("ai_reply_finish"),
    
    /**
     * 错误事件
     */
    ERROR("error");

    private final String eventType;

    SseEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventType() {
        return eventType;
    }
}