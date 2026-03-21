package com.project.aidoc.service.impl;

import com.project.aidoc.entity.UserConfig;
import com.project.aidoc.repository.UserConfigRepository;
import com.project.aidoc.service.UserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户配置服务实现类
 */
@Service
public class UserConfigServiceImpl implements UserConfigService {

    @Autowired
    private UserConfigRepository userConfigRepository;

    @Override
    public UserConfig getUserConfig(String userId) {
        // 查找该用户的所有配置，按创建时间倒序排列，取第一个 (最新的)
        java.util.List<UserConfig> configs = userConfigRepository.findByUserIdOrderByCreateTimeDesc(userId);
        if (!configs.isEmpty()) {
            return configs.get(0); // 返回最新的配置
        }
        // 如果没有找到配置，则创建默认配置
        return createDefaultUserConfig(userId);
    }

    @Override
    public UserConfig saveUserConfig(String userId, UserConfig config) {
        // 确保配置属于当前用户
        config.setUserId(userId);
        return userConfigRepository.save(config);
    }

    @Override
    public UserConfig createDefaultUserConfig(String userId) {
        UserConfig defaultConfig = new UserConfig();
        defaultConfig.setUserId(userId);

        // 设置默认值
        defaultConfig.setSiliconFlowBaseUrl("https://api.siliconflow.cn/v1/chat/completions");
        defaultConfig.setChatModelName("deepseek-ai/DeepSeek-V3.2");
        defaultConfig.setDecisionModelName("Qwen/Qwen2.5-7B-Instruct");
        defaultConfig.setAnalysisModelName("deepseek-ai/DeepSeek-V3.2");

        return userConfigRepository.save(defaultConfig);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanExpiredConfigs(String userId) {
        // 获取用户的所有配置（按创建时间倒序排列）
        List<UserConfig> configs = userConfigRepository.findByUserIdOrderByCreateTimeDesc(userId);
        
        System.out.println("=== 清理配置缓存开始 ===");
        System.out.println("用户 ID: " + userId);
        System.out.println("找到的配置数量：" + (configs == null ? 0 : configs.size()));
        
        // 如果没有配置或只有一个配置，不需要清理
        if (configs == null || configs.size() <= 1) {
            System.out.println("配置数量 <= 1，不需要清理");
            System.out.println("=== 清理配置缓存结束 ===");
            return;
        }
        
        // 保留最新的配置（第一个），删除其他所有配置
        String latestConfigId = configs.get(0).getId();
        System.out.println("保留最新配置 ID: " + latestConfigId);
        System.out.println("将删除 " + (configs.size() - 1) + " 个过期配置");
        
        // 执行删除操作
        try {
            userConfigRepository.deleteByUserIdAndIdNot(userId, latestConfigId);
            System.out.println("✓ 成功删除过期配置");
        } catch (Exception e) {
            System.out.println("✗ 删除过期配置失败：" + e.getMessage());
            e.printStackTrace();
            throw e; // 抛出异常，触发事务回滚
        }
        
        System.out.println("=== 清理配置缓存结束 ===");
    }
}
