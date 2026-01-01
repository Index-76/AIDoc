package com.project.aidoc.mapper;

import com.project.aidoc.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    /**
     * 根据用户ID查找用户
     */
    User selectById(@Param("userid") Long userid);
    
    /**
     * 根据用户名查找用户
     */
    User findByUsername(@Param("username") String username);
    
    /**
     * 根据邮箱查找用户
     */
    User findByEmail(@Param("email") String email);
    
    /**
     * 插入新用户
     */
    void insert(User user);
    
    /**
     * 根据ID更新用户
     */
    void updateById(User user);
    
    /**
     * 根据用户名更新密码
     */
    int updatePasswordByUsername(@Param("username") String username, @Param("newPassword") String newPassword);
    
    /**
     * 根据ID删除用户
     */
    int deleteById(@Param("userid") Long userid);
}