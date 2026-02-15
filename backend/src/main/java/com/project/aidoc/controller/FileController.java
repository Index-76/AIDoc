package com.project.aidoc.controller;

import com.project.aidoc.common.Result;
import com.project.aidoc.entity.File;
import com.project.aidoc.service.FileService;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @GetMapping
    public Result<List<File>> getFiles() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        List<File> files = fileService.getFilesByUserId(userId);
        return Result.success(files);
    }

    @PostMapping("/upload")
    public Result<File> uploadFile(
            @RequestParam(value = "section", required = false, defaultValue = "") String section,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "upload", required = false) MultipartFile upload,
            @RequestPart(value = "files", required = false) MultipartFile files) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        // 尝试从多个可能的参数名获取文件
        MultipartFile multipartFile = null;
        if (file != null && !file.isEmpty()) {
            multipartFile = file;
        } else if (upload != null && !upload.isEmpty()) {
            multipartFile = upload;
        } else if (files != null && !files.isEmpty()) {
            multipartFile = files;
        }

        if (multipartFile == null || multipartFile.isEmpty()) {
            return Result.error(400, "文件不能为空或未找到名为'file'、'upload'或'files'的参数，请确保前端使用multipart/form-data格式发送请求");
        }

        try {
            Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
            File savedFile = fileService.saveFile(multipartFile, section, userId);
            return Result.success(savedFile);
        } catch (Exception e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<?> downloadFile(@PathVariable("fileId") String fileId, HttpServletResponse response) {
        if (!StpUtil.isLogin()) {
            return ResponseEntity.status(401).body(Result.error(401, "用户未登录"));
        }

        try {
            Long userId = Long.parseLong(StpUtil.getLoginIdAsString());

            // 先获取文件信息以获取原始文件名
            com.project.aidoc.entity.File fileInfo = fileService.getFileById(fileId, userId);
            if (fileInfo == null) {
                return ResponseEntity.status(404).body(Result.error(404, "文件不存在或无权限访问"));
            }

            byte[] fileContent = fileService.getFileContent(fileId, userId);

            if (fileContent == null) {
                return ResponseEntity.status(404).body(Result.error(404, "文件内容不存在或无权限访问"));
            }

            HttpHeaders headers = new HttpHeaders();

            // 根据文件扩展名设置正确的Content-Type
            String fileExtension = getFileExtension(fileInfo.getFileName());
            String contentType = determineContentType(fileExtension);
            headers.setContentType(MediaType.parseMediaType(contentType));

            // 使用原始文件名，解决中文字符编码问题
            String originalFilename = fileInfo.getOriginalName();
            if (originalFilename == null || originalFilename.isEmpty()) {
                originalFilename = fileId + "." + fileExtension;
            }

            // 对文件名进行URL编码以解决中文字符问题
            String encodedFilename = java.net.URLEncoder.encode(originalFilename, "UTF-8").replace("+", "%20");

            headers.add("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            headers.setContentLength(fileContent.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new ByteArrayResource(fileContent));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Result.error(500, "下载失败: " + e.getMessage()));
        }
    }

    // 根据文件扩展名确定Content-Type
    private String determineContentType(String fileExtension) {
        if (fileExtension == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        switch (fileExtension.toLowerCase()) {
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt":
                return "text/plain";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "html":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "application/javascript";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            default:
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    // 辅助方法：获取文件扩展名
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    @DeleteMapping("/{fileId}/delete")
    public Result<String> deleteFile(@PathVariable("fileId") String fileId) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());

        try {
            fileService.deleteFileById(fileId, userId);
            return Result.success("文件删除成功");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/{fileId}/move")
    public Result<File> moveFile(@PathVariable("fileId") String fileId, @RequestBody MoveFileRequest request) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        File movedFile = fileService.moveFileToSection(fileId, request.getDestinationSectionId(), userId);

        if (movedFile != null) {
            return Result.success(movedFile);
        } else {
            return Result.error(404, "文件不存在或无权限操作");
        }
    }

    @PostMapping("/{fileId}/rename")
    public Result<File> renameFile(@PathVariable("fileId") String fileId, @RequestBody RenameFileRequest request) {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }

        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        File renamedFile = fileService.renameFile(fileId, request.getNewName(), userId);

        if (renamedFile != null) {
            return Result.success(renamedFile);
        } else {
            return Result.error(404, "文件不存在或无权限操作");
        }
    }

    // 内部类用于接收移动文件的请求体
    public static class MoveFileRequest {
        private String destinationSectionId;

        public String getDestinationSectionId() {
            return destinationSectionId;
        }

        public void setDestinationSectionId(String destinationSectionId) {
            this.destinationSectionId = destinationSectionId;
        }
    }

    // 内部类用于接收重命名文件的请求体
    @Data
    public static class RenameFileRequest {
        private String newName;
    }
}