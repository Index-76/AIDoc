package com.project.aidoc.repository;

import com.project.aidoc.entity.UserConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserConfigRepository extends MongoRepository<UserConfig, String> {
    Optional<UserConfig> findByUserId(Long userId);

    // 查询所有匹配的配置并按创建时间排序，只取最新的
    List<UserConfig> findByUserIdOrderByCreateTimeDesc(Long userId);
}