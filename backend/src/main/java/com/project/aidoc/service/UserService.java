package com.project.aidoc.service;

import com.project.aidoc.entity.User;
import java.util.List;

public interface UserService {
    List<User> getAllUsers();
    User getUserById(Long id);
    User createUser(User user);
    User updateUser(User user);
    void deleteUser(Long id);
    User getUserByUsername(String username);
    User getUserByEmail(String email);
    
    // 在创建用户时初始化配置
    void initializeUserConfig(Long userId);
    
    // 验证用户密码
    boolean verifyPassword(String username, String password);
}