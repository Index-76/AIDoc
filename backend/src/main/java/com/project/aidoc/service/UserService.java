package com.project.aidoc.service;

import com.project.aidoc.entity.User;

public interface UserService {
    /**
     * 根据用户ID查找用户
     */
    User findById(Long id);

    /**
     * 根据用户名查找用户
     */
    User findByUsername(String username);

    /**
     * 根据邮箱查找用户
     */
    User findByEmail(String email);

    /**
     * 创建新用户
     */
    User createUser(User user);

    /**
     * 更新用户密码
     */
    boolean updatePassword(String username, String newPassword);

    /**
     * 验证用户密码
     */
    boolean verifyPassword(String username, String password);
}