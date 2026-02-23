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
    public UserConfig getUserConfig(Long userId) {
        return userConfigService.getUserConfig(userId);
    }
    
    /**
     * 保存用户配置
     */
    public UserConfig saveUserConfig(Long userId, UserConfig config) {
        return userConfigService.saveUserConfig(userId, config);
    }
    
    /**
     * 创建默认用户配置
     */
    public UserConfig createDefaultUserConfig(Long userId) {
        return userConfigService.createDefaultUserConfig(userId);
    }
    
    /**
     * 获取决策AI API地址
     */
    public String getDecisionAiApi() {
        // 这里可以根据需要从配置中获取，暂时返回默认值
        return "https://api.siliconflow.cn/v1/chat/completions";
    }
    
    /**
     * 获取决策AI API密钥
     */
    public String getDecisionAiKey() {
        // 这里可以从环境变量或配置文件中获取
        return System.getenv("DECISION_AI_API_KEY");
    }
    
    /**
     * 获取决策AI模型名称
     */
    public String getDecisionAiModel() {
        return "Qwen/Qwen3-7B-Instruct";
    }
}