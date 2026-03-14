package com.project.aidoc.config;

import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.service.UserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 系统配置管理器 - 通过MongoDB管理用户配置
 */
@Component
public class SystemConfigManager {

    @Autowired
    private UserConfigService userConfigService;

    /**
     * 获取指定用户的配置
     */
    public UserConfig getUserConfig(String userId) {
        return userConfigService.getUserConfig(userId);
    }

    /**
     * 保存用户配置
     */
    public UserConfig saveUserConfig(String userId, UserConfig config) {
        return userConfigService.saveUserConfig(userId, config);
    }

    /**
     * 创建默认用户配置
     */
    public UserConfig createDefaultUserConfig(String userId) {
        return userConfigService.createDefaultUserConfig(userId);
    }

    /**
     * 获取决策AI API 地址
     */
    public String getDecisionAiApi(String userId) {
        UserConfig userConfig = getUserConfig(userId);
        if (userConfig != null && userConfig.getSiliconFlowBaseUrl() != null &&
                !userConfig.getSiliconFlowBaseUrl().isEmpty()) {
            return userConfig.getSiliconFlowBaseUrl();
        }
        // 默认 API 地址
        return "https://api.siliconflow.cn/v1/chat/completions";
    }

    /**
     * 获取决策AI API地址（向后兼容）
     */
    public String getDecisionAiApi() {
        // 保持向后兼容
        return "https://api.siliconflow.cn/v1/chat/completions";
    }

    /**
     * 获取决策AI API密钥
     */
    public String getDecisionAiKey(String userId) {
        UserConfig userConfig = getUserConfig(userId);
        if (userConfig != null && userConfig.getSiliconFlowApiKey() != null) {
            return userConfig.getSiliconFlowApiKey();
        }
        return null;
    }

    /**
     * 获取决策AI 模型名称
     */
    public String getDecisionAiModel(String userId) {
        UserConfig userConfig = getUserConfig(userId);
        if (userConfig != null && userConfig.getDecisionModelName() != null &&
                !userConfig.getDecisionModelName().isEmpty()) {
            return userConfig.getDecisionModelName();
        }
        // 默认模型
        return "Qwen/Qwen2.5-7B-Instruct";
    }

    /**
     * 获取默认决策AI模型名称（向后兼容）
     */
    public String getDecisionAiModel() {
        // 默认模型
        return "Qwen/Qwen2.5-7B-Instruct";
    }
}