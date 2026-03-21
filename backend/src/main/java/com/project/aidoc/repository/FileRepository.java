package com.project.aidoc.repository;

import com.project.aidoc.entity.File;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FileRepository extends MongoRepository<File, String> {
    List<File> findByUserId(String userId);
    List<File> findByUserIdAndSection(String userId, String section);
    void deleteByUserIdAndId(String userId, String fileId);
    void deleteByUserIdAndSection(String userId, String section);
}
