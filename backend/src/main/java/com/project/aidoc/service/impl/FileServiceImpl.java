package com.project.aidoc.service.impl;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.project.aidoc.common.utils.ExtractText;
import com.project.aidoc.entity.File;
import com.project.aidoc.repository.FileRepository;
import com.project.aidoc.service.FileService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Override
    public File saveFile(MultipartFile file, String section, String userId) throws Exception {
        // 生成唯一的文件名
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

        // 将文件保存到 GridFS
        ObjectId objectId = gridFsTemplate.store(file.getInputStream(), fileName, file.getContentType());

        // 创建文件实体并保存到 MongoDB
        File fileEntity = new File();
        fileEntity.setId(objectId.toString());
        fileEntity.setFileName(fileName);
        fileEntity.setOriginalName(file.getOriginalFilename());
        fileEntity.setContentType(file.getContentType());
        fileEntity.setSize(file.getSize());
        fileEntity.setSection(section);
        fileEntity.setUserId(userId);
        fileEntity.setUploadTime(LocalDateTime.now());
        fileEntity.setFilePath("/api/v1/files/" + objectId.toString() + "/download");

        // 处理文件转换：将 Word、Excel、Markdown 转换为 TXT 并存入 temp 区域
        processFileConversion(file, objectId.toString(), userId);

        return fileRepository.save(fileEntity);
    }

    /**
     * 处理文件转换逻辑
     * - Excel: 跳过处理
     * - TXT: 直接复制到 temp 区域
     * - Word/Markdown: 转换为 TXT 后存入 temp 区域
     */
    private void processFileConversion(MultipartFile file, String fileId, String userId) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return;
        }

        String fileExtension = getFileExtension(originalFilename).toLowerCase();
        
        // 获取文件内容
        byte[] fileContent = file.getBytes();

        // Excel 文件跳过处理
        if ("xls".equals(fileExtension) || "xlsx".equals(fileExtension)) {
            return;
        }

        // TXT 文件直接复制到 temp 区域
        if ("txt".equals(fileExtension)) {
            saveTextToTemp(fileContent, fileId, userId, originalFilename);
            return;
        }

        // Word 和 Markdown 文件转换为 TXT 后存入 temp 区域
        if ("doc".equals(fileExtension) || "docx".equals(fileExtension) || 
            "md".equals(fileExtension) || "markdown".equals(fileExtension)) {
            
            try {
                String textContent = ExtractText.convertToText(fileContent, originalFilename);
                byte[] textBytes = textContent.getBytes("UTF-8");
                saveTextToTemp(textBytes, fileId, userId, originalFilename);
            } catch (Exception e) {
                // 转换失败不阻断主流程，记录日志即可
                System.err.println("文件转换失败：" + originalFilename + ", 错误：" + e.getMessage());
            }
        }
    }

    /**
     * 将文本内容保存到 MongoDB 的 temp 区域
     */
    private void saveTextToTemp(byte[] textContent, String sourceFileId, String userId, String originalName) throws Exception {
        // 生成文件名：【文件 id】_text.txt
        String textFileName = sourceFileId + "_text.txt";
        
        // 提取原始文件名不带扩展名的部分
        String nameWithoutExtension = originalName;
        int lastDotIndex = originalName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            nameWithoutExtension = originalName.substring(0, lastDotIndex);
        }
        String textOriginalName = "[" + sourceFileId + "]_" + nameWithoutExtension + ".txt";

        // 将文本内容存储到 GridFS
        ObjectId textObjectId = gridFsTemplate.store(
            new ByteArrayInputStream(textContent), 
            textFileName, 
            "text/plain"
        );

        // 创建文件实体
        File textFileEntity = new File();
        textFileEntity.setId(textObjectId.toString());
        textFileEntity.setFileName(textFileName);
        textFileEntity.setOriginalName(textOriginalName);
        textFileEntity.setContentType("text/plain");
        textFileEntity.setSize(textContent.length);
        textFileEntity.setSection("temp");
        textFileEntity.setUserId(userId);
        textFileEntity.setUploadTime(LocalDateTime.now());
        textFileEntity.setFilePath("/api/v1/files/" + textObjectId.toString() + "/download");

        // 保存到 MongoDB
        fileRepository.save(textFileEntity);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    @Override
    public List<File> getFilesByUserId(String userId) {
        return fileRepository.findByUserId(userId);
    }

    @Override
    public List<File> getFilesByUserIdAndSection(String userId, String section) {
        return fileRepository.findByUserIdAndSection(userId, section);
    }

    @Override
    public void deleteFileById(String fileId, String userId) {
        // 检查文件是否存在
        Optional<File> fileOpt = fileRepository.findById(fileId);
        if (!fileOpt.isPresent() || !fileOpt.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("文件不存在: " + fileId);
        }

        // 文件存在，执行删除操作
        // 从 GridFS 删除文件
        gridFsTemplate.delete(new Query(Criteria.where("_id").is(fileId)));

        // 从 MongoDB 删除文件元数据
        fileRepository.deleteByUserIdAndId(userId, fileId);
    }

    @Override
    public File moveFileToSection(String fileId, String destinationSectionId, String userId) {
        Optional<File> fileOpt = Optional.ofNullable(fileRepository.findById(fileId).orElse(null));
        if (fileOpt.isPresent() && fileOpt.get().getUserId().equals(userId)) {
            File file = fileOpt.get();
            file.setSection(destinationSectionId);
            return fileRepository.save(file);
        }
        return null;
    }

    @Override
    public File renameFile(String fileId, String newName, String userId) {
        Optional<File> fileOpt = Optional.ofNullable(fileRepository.findById(fileId).orElse(null));
        if (fileOpt.isPresent() && fileOpt.get().getUserId().equals(userId)) {
            File file = fileOpt.get();
            file.setOriginalName(newName);
            return fileRepository.save(file);
        }
        return null;
    }

    @Override
    public byte[] getFileContent(String fileId, String userId) {
        Optional<File> fileOpt = Optional.ofNullable(fileRepository.findById(fileId).orElse(null));
        if (fileOpt.isPresent() && fileOpt.get().getUserId().equals(userId)) {
            // 从 GridFS 获取文件内容
            GridFSFile gridFsFile = gridFsTemplate.findOne(new Query(Criteria.where("_id").is(fileId)));

            if (gridFsFile != null) {
                try {
                    // 使用 GridFSFile 的 id 直接获取文件内容
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    gridFsTemplate.getResource(gridFsFile).getInputStream().transferTo(outputStream);
                    return outputStream.toByteArray();
                } catch (IOException e) {
                    throw new RuntimeException("无法读取文件内容: " + e.getMessage(), e);
                }
            }
        }
        return null;
    }

    @Override
    public File getFileById(String fileId, String userId) {
        Optional<File> fileOpt = fileRepository.findById(fileId);
        if (fileOpt.isPresent() && fileOpt.get().getUserId().equals(userId)) {
            return fileOpt.get();
        }
        return null;
    }
}