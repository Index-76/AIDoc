-- AIDoc项目数据库表结构定义
-- 包含: users表

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS aidoc_dev
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE aidoc_dev;

-- 创建用户表 (users)
CREATE TABLE users (
    userid BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID，主键',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名，唯一',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '用户邮箱，唯一',
    password VARCHAR(255) NOT NULL COMMENT '用户密码，加密存储',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    status TINYINT DEFAULT 1 COMMENT '用户状态: 1-正常, 0-禁用',
    INDEX idx_username (username),
    INDEX idx_email (email)
) COMMENT='用户账号表';