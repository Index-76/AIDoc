package com.project.aidoc.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "user_configs")
public class UserConfig {
    @Id
    private String id;
    
    @Indexed
    private Long userId; // 所属用户ID
    
    // 硅基流动API配置
    private String siliconFlowApiKey;
    private String siliconFlowBaseUrl;
    private String chatModelName;
    private String decisionModelName;
    private String analysisModelName;
    
    @Indexed
    private LocalDateTime createTime;
    @Indexed
    private LocalDateTime updateTime;

    public UserConfig() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        // 设置默认值
        this.siliconFlowBaseUrl = "https://api.siliconflow.cn/v1/chat/completions";
        this.chatModelName = "deepseek-ai/DeepSeek-V3.2";
        this.decisionModelName = "Qwen/Qwen2.5-7B-Instruct";
        this.analysisModelName = "deepseek-ai/DeepSeek-V3.2";
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSiliconFlowApiKey() {
        return siliconFlowApiKey;
    }

    public void setSiliconFlowApiKey(String siliconFlowApiKey) {
        this.siliconFlowApiKey = siliconFlowApiKey;
    }

    public String getSiliconFlowBaseUrl() {
        return siliconFlowBaseUrl;
    }

    public void setSiliconFlowBaseUrl(String siliconFlowBaseUrl) {
        this.siliconFlowBaseUrl = siliconFlowBaseUrl;
    }

    public String getChatModelName() {
        return chatModelName;
    }

    public void setChatModelName(String chatModelName) {
        this.chatModelName = chatModelName;
    }

    public String getDecisionModelName() {
        return decisionModelName;
    }

    public void setDecisionModelName(String decisionModelName) {
        this.decisionModelName = decisionModelName;
    }

    public String getAnalysisModelName() {
        return analysisModelName;
    }

    public void setAnalysisModelName(String analysisModelName) {
        this.analysisModelName = analysisModelName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}