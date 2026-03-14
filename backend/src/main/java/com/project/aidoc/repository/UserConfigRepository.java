package com.project.aidoc.repository;

import com.project.aidoc.entity.UserConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户配置仓库接口
 */
@Repository
public interface UserConfigRepository extends MongoRepository<UserConfig, String> {
    
    /**
     * 根据用户 ID 查找所有配置，按创建时间倒序排列
     */
    List<UserConfig> findByUserIdOrderByCreateTimeDesc(String userId);
    
    /**
     * 根据用户 ID 删除所有配置
     */
    void deleteByUserId(String userId);
}
