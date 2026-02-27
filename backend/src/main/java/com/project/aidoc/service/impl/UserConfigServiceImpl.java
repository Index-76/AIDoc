package com.project.aidoc.service.impl;

import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.repository.UserConfigRepository;
import com.project.aidoc.service.UserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserConfigServiceImpl implements UserConfigService {

    @Autowired
    private UserConfigRepository userConfigRepository;

    @Override
    public UserConfig getUserConfig(Long userId) {
        // 查找该用户的所有配置，按创建时间倒序排列，取第一个（最新的）
        List<UserConfig> configs = userConfigRepository.findByUserIdOrderByCreateTimeDesc(userId);
        if (!configs.isEmpty()) {
            return configs.get(0); // 返回最新的配置
        }
        // 如果没有找到配置，则创建默认配置
        return createDefaultUserConfig(userId);
    }

    @Override
    public UserConfig saveUserConfig(Long userId, UserConfig config) {
        // 确保配置属于当前用户
        config.setUserId(userId);
        return userConfigRepository.save(config);
    }

    @Override
    public UserConfig createDefaultUserConfig(Long userId) {
        UserConfig defaultConfig = new UserConfig();
        defaultConfig.setUserId(userId);

        // 设置默认值
        defaultConfig.setSiliconFlowBaseUrl("https://api.siliconflow.cn/v1/chat/completions");
        defaultConfig.setChatModelName("deepseek-ai/DeepSeek-V3.2");
        defaultConfig.setDecisionModelName("Qwen/Qwen2.5-7B-Instruct");
        defaultConfig.setAnalysisModelName("deepseek-ai/DeepSeek-V3.2");

        return userConfigRepository.save(defaultConfig);
    }
}