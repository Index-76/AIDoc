package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.common.dto.LoginRequest;
import com.project.aidoc.common.dto.LoginResponse;
import com.project.aidoc.entity.User;
import com.project.aidoc.service.UserService;
import com.project.aidoc.service.FileService;
import com.project.aidoc.service.UserConfigService;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

// Jakarta EE imports instead of Java EE
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private UserConfigService userConfigService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        // 空值检查
        if (loginRequest == null || loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            return Result.error(400, "用户名和密码不能为空");
        }

        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        // 验证用户凭据
        boolean isValid = userService.verifyPassword(username, password);
        if (!isValid) {
            return Result.error(401, "用户名或密码错误");
        }

        // 获取用户信息
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return Result.error(401, "用户不存在");
        }

        // 登录并生成 token（使用 MongoDB 的 String ID）
        StpUtil.login(user.getId());

        // 返回登录响应
        LoginResponse response = new LoginResponse(StpUtil.getTokenValue(), user, "登录成功");
        return Result.success(response);
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        // 检查必填字段是否为空
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error(400, "用户名不能为空");
        }

        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            return Result.error(400, "邮箱不能为空");
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return Result.error(400, "密码不能为空");
        }

        try {
            // 检查用户名是否已存在
            if (userService.getUserByUsername(user.getUsername()) != null) {
                return Result.error(409, "用户名已存在");
            }

            // 检查邮箱是否已存在
            if (user.getEmail() != null && userService.getUserByEmail(user.getEmail()) != null) {
                return Result.error(409, "邮箱已被注册");
            }

            // 创建新用户
            User newUser = userService.createUser(user);

            // 登录新用户
            StpUtil.login(newUser.getId());

            return Result.success("注册成功");
        } catch (Exception e) {
            return Result.error(500, "注册失败：" + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        // 检查用户是否已登录
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        // 登出
        StpUtil.logout();
        return Result.success("退出成功");
    }

    @GetMapping("/me")
    public Result<User> getCurrentUser() {
        // 验证 token 并获取用户信息
        if (StpUtil.isLogin()) {
            Object loginId = StpUtil.getLoginId();
            try {
                // loginId 现在是 MongoDB 的 String 类型 ID
                String userId = loginId.toString();
                User user = userService.getUserById(userId);
                if (user != null) {
                    return Result.success(user);
                }
            } catch (Exception e) {
                return Result.error(401, "获取用户信息失败");
            }
        }

        return Result.error(401, "用户未登录");
    }

    /**
     * 清理缓存
     * 支持两种清理类型：
     * 1. cleanContent = "file": 清理文件缓存（删除用户在 files 表中 section 为 temp 的全部文件）
     * 2. cleanContent = "config": 清理配置缓存（删除用户在 userConfigs 中除最新一条配置以外的全部过期配置）
     */
    @PostMapping("/clean")
    public Result<Map<String, Object>> cleanCache(@RequestBody Map<String, String> request) {
        // 检查用户是否已登录
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        String userId = StpUtil.getLoginIdAsString();
        String cleanContent = request.get("cleanContent");

        Map<String, Object> result = new HashMap<>();

        System.out.println("userId: " + userId);
        System.out.println("cleanContent: " + cleanContent);

        try {
            if ("file".equals(cleanContent)) {
                // 清理文件缓存
                fileService.cleanTempFiles(userId);
                result.put("message", "文件缓存清理成功");
                result.put("cleanedType", "file");
            } else if ("config".equals(cleanContent)) {
                // 清理配置缓存
                userConfigService.cleanExpiredConfigs(userId);
                result.put("message", "配置缓存清理成功");
                result.put("cleanedType", "config");
            } else {
                return Result.error(400, "不支持的清理类型：" + cleanContent);
            }
            
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(500, "清理失败：" + e.getMessage());
        }
    }
}
