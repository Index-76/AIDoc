package com.project.aidoc.service;

import com.project.aidoc.entity.UserConfig;

public interface UserConfigService {
    UserConfig getUserConfig(Long userId);
    UserConfig saveUserConfig(Long userId, UserConfig config);
    UserConfig createDefaultUserConfig(Long userId);
}