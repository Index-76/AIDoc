package com.project.aidoc.common.tools;

import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 目录查看工具类
 * 用于查看用户文件目录情况并返回目录信息
 */
@Component
public class DirectoryViewerTool {
    
    @Autowired
    private FileService fileService;
    
    /**
     * 查看指定用户的所有文件目录情况
     * @param userId 用户 ID (String ObjectId)
     * @return 包含目录信息的 Map
     */
    public Map<String, Object> viewUserDirectory(String userId) {
        try {
            // 获取用户的所有文件
            List<File> userFiles = fileService.getFilesByUserId(userId);
            
            // 过滤掉 temp 区域的文件
            userFiles = userFiles.stream()
                    .filter(file -> !"temp".equals(file.getSection()))
                    .collect(Collectors.toList());
            
            // 按分区组织文件
            Map<String, List<File>> filesBySection = new HashMap<>();
            for (File file : userFiles) {
                String section = normalizeSection(file.getSection());
                filesBySection.computeIfAbsent(section, k -> new ArrayList<>()).add(file);
            }
            
            // 准备返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("message", "已为您查看了当前的目录结构");
            result.put("filesBySection", filesBySection);
            result.put("totalFiles", userFiles.size());
            
            return result;
            
        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("message", "查看目录时发生错误：" + e.getMessage());
            return errorResult;
        }
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + "B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2fKB", size / 1024.0);
        } else {
            return String.format("%.2fMB", size / (1024.0 * 1024));
        }
    }
    
    /**
     * 为 AI 准备目录信息
     */
    public String prepareDirectoryInfoForAi(List<File> files) {
        if (files == null || files.isEmpty()) {
            return "暂无文件";
        }
        
        // 过滤掉 temp 区域的文件
        files = files.stream()
                .filter(file -> !"temp".equals(file.getSection()))
                .collect(Collectors.toList());
        
        if (files.isEmpty()) {
            return "暂无文件";
        }
        
        // 按分区组织文件
        Map<String, List<File>> filesBySection = new HashMap<>();
        for (File file : files) {
            String section = normalizeSection(file.getSection());
            filesBySection.computeIfAbsent(section, k -> new ArrayList<>()).add(file);
        }
        
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy 年 MM 月 dd 日");
        
        // 四个标准分区
        String[] standardSections = {"等待", "读取", "模板", "结果"};
        String[] sectionNames = {"等待区", "读取区", "模板区", "结果区"};
        
        for (int i = 0; i < standardSections.length; i++) {
            String sectionKey = standardSections[i];
            String sectionDisplayName = sectionNames[i];
            List<File> sectionFiles = filesBySection.getOrDefault(sectionKey, new ArrayList<>());
            
            sb.append(i + 1).append(".  **").append(sectionDisplayName).append("**：");
            
            if (sectionFiles.isEmpty()) {
                sb.append("暂无文件\n");
            } else {
                sb.append(sectionFiles.size()).append("个文件\n");
                for (File file : sectionFiles) {
                    sb.append("    *   `").append(file.getOriginalName())
                      .append("` (").append(formatFileSize(file.getSize())).append(")\n");
                }
            }
            sb.append("\n"); // 添加空行分隔
        }
        
        return sb.toString().trim();
    }
    
    /**
     * 规范化分区名称
     */
    private String normalizeSection(String section) {
        if (section == null) {
            return "等待";
        }
        
        switch (section.toLowerCase()) {
            case "wait":
            case "等待":
                return "等待";
            case "read":
            case "读取":
                return "读取";
            case "template":
            case "模板":
                return "模板";
            case "result":
            case "结果":
                return "结果";
            default:
                return "等待"; // 默认归类到等待区
        }
    }
    
    /**
     * 生成固定格式的目录输出
     */
    public String generateFixedFormatDirectoryOutput(List<File> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("已为您查看了当前的目录结构，包含四个分区：\n\n");
        
        String directoryInfo = prepareDirectoryInfoForAi(files);
        sb.append(directoryInfo);
        sb.append("\n请问您想对以上哪个文件进行操作，或者需要什么具体的帮助吗？");
        
        return sb.toString();
    }
}