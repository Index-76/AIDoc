package com.project.aidoc.service;

import com.project.aidoc.entity.File;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface FileService {
    File saveFile(MultipartFile file, String section, Long userId) throws Exception;

    List<File> getFilesByUserId(Long userId);

    List<File> getFilesByUserIdAndSection(Long userId, String section);

    void deleteFileById(String fileId, Long userId);

    File moveFileToSection(String fileId, String destinationSectionId, Long userId);

    File renameFile(String fileId, String newName, Long userId);

    byte[] getFileContent(String fileId, Long userId);

    File getFileById(String fileId, Long userId);
}