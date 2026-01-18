package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.config.SystemConfigManager;
import com.project.aidoc.entity.UserConfig;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user-config")
public class SystemConfigController {

    @Autowired
    private SystemConfigManager systemConfigManager;

    @GetMapping
    public Result<UserConfig> getUserConfig() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        try {
            Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
            UserConfig config = systemConfigManager.getUserConfig(userId);
            return Result.success(config);
        } catch (Exception e) {
            return Result.error(500, "读取配置失败: " + e.getMessage());
        }
    }

    @PostMapping
    public Result<String> updateUserConfig(@RequestBody UserConfig config) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        try {
            Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
            systemConfigManager.saveUserConfig(userId, config);
            return Result.success("配置更新成功");
        } catch (Exception e) {
            return Result.error(500, "保存配置失败: " + e.getMessage());
        }
    }
}