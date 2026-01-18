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
    User selectByUsername(@Param("username") String username);
    
    /**
     * 根据邮箱查找用户
     */
    User selectByEmail(@Param("email") String email);
    
    /**
     * 插入新用户
     */
    void insert(User user);
    
    /**
     * 根据ID更新用户
     */
    void update(User user);
    
    /**
     * 根据用户名更新密码
     */
    int updatePasswordByUsername(@Param("username") String username, @Param("newPassword") String newPassword);
    
    /**
     * 根据ID删除用户
     */
    int delete(@Param("userid") Long userid);
    
    /**
     * 查询所有用户
     */
    java.util.List<User> selectAll();
}