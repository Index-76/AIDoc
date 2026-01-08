package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.entity.User;
import com.project.aidoc.service.UserService;
import com.project.aidoc.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

// Jakarta EE imports instead of Java EE
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private UserMapper userMapper;

    /**
     * 根据ID获取用户，仅允许用户访问自己的信息
     */
    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable("id") Long id) {
        // 检查用户是否已登录
        if (!cn.dev33.satoken.stp.StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }
        
        // 获取当前登录用户的ID
        Long currentUserId = Long.parseLong(cn.dev33.satoken.stp.StpUtil.getLoginIdAsString());
        
        // 检查请求的ID是否与当前登录用户ID匹配
        if (!currentUserId.equals(id)) {
            return Result.error(403, "无权访问其他用户信息");
        }
        
        User user = userMapper.selectById(id);
        if (user != null) {
            return Result.success(user);
        }
        return Result.error(404, "用户不存在");
    }

    /**
     * 创建新用户
     */
    @PostMapping
    public Result<User> createUser(@RequestBody User user) {
        try {
            User createdUser = userService.createUser(user);
            return Result.success(createdUser);
        } catch (Exception e) {
            return Result.error(500, "创建用户失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户信息，仅允许用户更新自己的信息
     */
    @PutMapping("/{id}")
    public Result<String> updateUser(@PathVariable("id") Long id, @RequestBody User user) {
        // 检查用户是否已登录
        if (!cn.dev33.satoken.stp.StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }
        
        // 获取当前登录用户的ID
        Long currentUserId = Long.parseLong(cn.dev33.satoken.stp.StpUtil.getLoginIdAsString());
        
        // 检查请求的ID是否与当前登录用户ID匹配
        if (!currentUserId.equals(id)) {
            return Result.error(403, "无权更新其他用户信息");
        }
        
        user.setUserid(id);
        try {
            userMapper.updateById(user);
            return Result.success("用户更新成功");
        } catch (Exception e) {
            return Result.error(500, "更新用户失败: " + e.getMessage());
        }
    }

    /**
     * 删除用户，仅允许用户删除自己的信息
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable("id") Long id) {
        // 检查用户是否已登录
        if (!cn.dev33.satoken.stp.StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }
        
        // 获取当前登录用户的ID
        Long currentUserId = Long.parseLong(cn.dev33.satoken.stp.StpUtil.getLoginIdAsString());
        
        // 检查请求的ID是否与当前登录用户ID匹配
        if (!currentUserId.equals(id)) {
            return Result.error(403, "无权删除其他用户信息");
        }
        
        int result = userMapper.deleteById(id);
        if (result > 0) {
            return Result.success("用户删除成功");
        }
        return Result.error(500, "删除用户失败");
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<User> getCurrentUser() {
        if (cn.dev33.satoken.stp.StpUtil.isLogin()) {
            try {
                Long userId = Long.parseLong(cn.dev33.satoken.stp.StpUtil.getLoginIdAsString());
                User user = userMapper.selectById(userId);
                if (user != null) {
                    return Result.success(user);
                }
            } catch (NumberFormatException e) {
                return Result.error(500, "获取用户信息失败");
            }
        }
        return Result.error(401, "用户未登录");
    }
}