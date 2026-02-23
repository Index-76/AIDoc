package com.project.aidoc.common.dto;

import lombok.Data;

/**
 * AI决策结果DTO
 */
@Data
public class AiDecisionResult {
    /**
     * 工具类型代码 (0-5)
     */
    private int toolCode;
    
    /**
     * 是否需要预处理
     */
    private boolean needPreprocess;
    
    /**
     * 决策说明
     */
    private String decisionReason;
    
    /**
     * 原始用户消息
     */
    private String originalMessage;
    
    public AiDecisionResult() {}
    
    public AiDecisionResult(int toolCode, boolean needPreprocess, String decisionReason, String originalMessage) {
        this.toolCode = toolCode;
        this.needPreprocess = needPreprocess;
        this.decisionReason = decisionReason;
        this.originalMessage = originalMessage;
    }
}