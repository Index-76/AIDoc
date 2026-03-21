package com.project.aidoc.service;

import com.project.aidoc.entity.File;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件服务接口
 */
public interface FileService {
    File saveFile(MultipartFile file, String section, String userId) throws Exception;

    List<File> getFilesByUserId(String userId);

    List<File> getFilesByUserIdAndSection(String userId, String section);

    void deleteFileById(String fileId, String userId);

    File moveFileToSection(String fileId, String destinationSectionId, String userId);

    File renameFile(String fileId, String newName, String userId);

    byte[] getFileContent(String fileId, String userId);

    File getFileById(String fileId, String userId);
    
    void cleanTempFiles(String userId);
}
