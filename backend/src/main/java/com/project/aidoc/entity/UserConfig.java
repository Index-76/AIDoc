package com.project.aidoc.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 用户配置实体类
 */
@Document(collection = "user_configs")
@Data
public class UserConfig {
    @Id
    private String id;

    @Indexed
    private String userId; // 所属用户 ID (MongoDB ObjectId)

    // 硅基流动 API 配置
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
}
