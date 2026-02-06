package com.project.aidoc.service.impl;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.project.aidoc.entity.File;
import com.project.aidoc.repository.FileRepository;
import com.project.aidoc.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.bson.types.ObjectId;

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
    public File saveFile(MultipartFile file, String section, Long userId) throws Exception {
        // 生成唯一的文件名
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        
        // 将文件保存到GridFS
        ObjectId objectId = gridFsTemplate.store(file.getInputStream(), fileName, file.getContentType());

        // 创建文件实体并保存到MongoDB
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

        return fileRepository.save(fileEntity);
    }

    @Override
    public List<File> getFilesByUserId(Long userId) {
        return fileRepository.findByUserId(userId);
    }

    @Override
    public List<File> getFilesByUserIdAndSection(Long userId, String section) {
        return fileRepository.findByUserIdAndSection(userId, section);
    }

    @Override
    public void deleteFileById(String fileId, Long userId) {
        // 检查文件是否存在
        Optional<File> fileOpt = fileRepository.findById(fileId);
        if (!fileOpt.isPresent() || !fileOpt.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("文件不存在: " + fileId);
        }
        
        // 文件存在，执行删除操作
        // 从GridFS删除文件
        gridFsTemplate.delete(new Query(Criteria.where("_id").is(fileId)));
        
        // 从MongoDB删除文件元数据
        fileRepository.deleteByUserIdAndId(userId, fileId);
    }

    @Override
    public File moveFileToSection(String fileId, String destinationSectionId, Long userId) {
        Optional<File> fileOpt = Optional.ofNullable(fileRepository.findById(fileId).orElse(null));
        if (fileOpt.isPresent() && fileOpt.get().getUserId().equals(userId)) {
            File file = fileOpt.get();
            file.setSection(destinationSectionId);
            return fileRepository.save(file);
        }
        return null;
    }

    @Override
    public File renameFile(String fileId, String newName, Long userId) {
        Optional<File> fileOpt = Optional.ofNullable(fileRepository.findById(fileId).orElse(null));
        if (fileOpt.isPresent() && fileOpt.get().getUserId().equals(userId)) {
            File file = fileOpt.get();
            file.setOriginalName(newName);
            return fileRepository.save(file);
        }
        return null;
    }

    @Override
    public byte[] getFileContent(String fileId, Long userId) {
        Optional<File> fileOpt = Optional.ofNullable(fileRepository.findById(fileId).orElse(null));
        if (fileOpt.isPresent() && fileOpt.get().getUserId().equals(userId)) {
            // 从GridFS获取文件内容
            GridFSFile gridFsFile = 
                gridFsTemplate.findOne(new Query(Criteria.where("_id").is(fileId)));
                
            if (gridFsFile != null) {
                try {
                    // 使用GridFSFile的id直接获取文件内容，而不是通过文件名
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    gridFsTemplate.getResource(gridFsFile).getInputStream().transferTo(outputStream);
                    return outputStream.toByteArray();
                } catch (IOException e) {
                    throw new RuntimeException("无法读取文件内容", e);
                }
            }
        }
        return null;
    }

    @Override
    public File getFileById(String fileId, Long userId) {
        Optional<File> fileOpt = fileRepository.findById(fileId);
        if (fileOpt.isPresent() && fileOpt.get().getUserId().equals(userId)) {
            return fileOpt.get();
        }
        return null;
    }
}