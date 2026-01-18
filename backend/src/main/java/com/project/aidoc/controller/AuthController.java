package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.common.dto.LoginRequest;
import com.project.aidoc.common.dto.LoginResponse;
import com.project.aidoc.entity.User;
import com.project.aidoc.service.UserService;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

// Jakarta EE imports instead of Java EE
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserService userService;

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

        // 登录并生成token
        StpUtil.login(user.getUserid());

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
            StpUtil.login(newUser.getUserid());

            return Result.success("注册成功");
        } catch (Exception e) {
            return Result.error(500, "注册失败: " + e.getMessage());
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
        // 验证token并获取用户信息
        if (StpUtil.isLogin()) {
            Object loginId = StpUtil.getLoginId();
            try {
                // 假设loginId是用户ID，通过ID获取用户
                Long userId = Long.parseLong(loginId.toString());
                User user = userService.getUserById(userId);
                if (user != null) {
                    return Result.success(user);
                }
            } catch (NumberFormatException e) {
                // 如果转换失败，可能是其他格式的登录ID
                User user = userService.getUserByUsername(loginId.toString());
                if (user != null) {
                    return Result.success(user);
                }
            }
        }

        return Result.error(401, "用户未登录");
    }
}