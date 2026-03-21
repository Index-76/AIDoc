package com.project.aidoc.service;

import com.project.aidoc.entity.UserConfig;

/**
 * 用户配置服务接口
 */
public interface UserConfigService {
    UserConfig getUserConfig(String userId);
    UserConfig saveUserConfig(String userId, UserConfig config);
    UserConfig createDefaultUserConfig(String userId);
    void cleanExpiredConfigs(String userId);
}
