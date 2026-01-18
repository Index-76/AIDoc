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
}