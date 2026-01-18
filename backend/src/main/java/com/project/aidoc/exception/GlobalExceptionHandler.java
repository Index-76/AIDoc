package com.project.aidoc.exception;

import cn.dev33.satoken.exception.NotLoginException;
import com.project.aidoc.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 全局异常处理器，整合所有异常处理逻辑
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<String> handleNotLoginException(NotLoginException e) {
        return Result.error(401, "用户未登录");
    }

    /**
     * 处理文件上传相关的异常
     */
    @ExceptionHandler(MultipartException.class)
    public Result<String> handleMultipartException(MultipartException e) {
        return Result.error(400, "文件上传格式错误：" + e.getMessage());
    }

    /**
     * 处理缺失请求部分的异常
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public Result<String> handleMissingServletRequestPartException(MissingServletRequestPartException e) {
        return Result.error(400, "文件参数缺失：" + e.getRequestPartName() + " 参数未找到，请确保前端使用multipart/form-data格式发送请求");
    }

    /**
     * 处理其他一般异常
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleGeneralException(Exception e) {
        return Result.error(500, "服务器内部错误：" + e.getMessage());
    }
}