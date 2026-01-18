package com.project.aidoc.service.impl;

import com.project.aidoc.entity.User;
import com.project.aidoc.mapper.UserMapper;
import com.project.aidoc.service.UserService;
import com.project.aidoc.service.UserConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserConfigService userConfigService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public User createUser(User user) {
        // 检查用户名或邮箱是否已存在
        if (getUserByUsername(user.getUsername()) != null || getUserByEmail(user.getEmail()) != null) {
            throw new RuntimeException("用户名或邮箱已存在");
        }
        
        // 加密密码
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        
        userMapper.insert(user);
        
        // 初始化用户配置
        initializeUserConfig(user.getUserid());
        
        return user;
    }

    @Override
    public User updateUser(User user) {
        userMapper.update(user);
        return user;
    }

    @Override
    public void deleteUser(Long id) {
        userMapper.delete(id);
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public User getUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    @Override
    public void initializeUserConfig(Long userId) {
        // 创建默认用户配置
        userConfigService.createDefaultUserConfig(userId);
    }

    @Override
    public boolean verifyPassword(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return false;
        }
        
        // 使用BCryptPasswordEncoder验证密码
        return passwordEncoder.matches(password, user.getPassword());
    }
}