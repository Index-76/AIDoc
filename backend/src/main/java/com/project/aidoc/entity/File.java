package com.project.aidoc.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "files")
@Data
public class File {
    @Id
    private String id;
    
    private String fileName;
    private String originalName;
    private String contentType;
    private long size;
    private String section; // 文件分类
    private Long userId; // 所属用户ID
    private LocalDateTime uploadTime;
    private String filePath; // 文件存储路径
    
    public File() {
        this.uploadTime = LocalDateTime.now();
    }
}