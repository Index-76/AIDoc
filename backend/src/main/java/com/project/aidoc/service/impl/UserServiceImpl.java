package com.project.aidoc.service.impl;

import com.project.aidoc.entity.User;
import com.project.aidoc.mapper.UserMapper;
import com.project.aidoc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public User findByEmail(String email) {
        return userMapper.findByEmail(email);
    }

    @Override
    public User createUser(User user) {
        // 使用BCryptPasswordEncoder加密密码
        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        userMapper.insert(user);
        return user;
    }

    @Override
    public boolean updatePassword(String username, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        int result = userMapper.updatePasswordByUsername(username, encodedPassword);
        return result > 0;
    }

    @Override
    public boolean verifyPassword(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            return false;
        }
        
        // 使用BCryptPasswordEncoder验证密码
        return passwordEncoder.matches(password, user.getPassword());
    }
}