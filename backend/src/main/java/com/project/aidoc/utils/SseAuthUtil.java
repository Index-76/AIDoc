package com.project.aidoc.utils;

import cn.dev33.satoken.stp.StpUtil;
import com.project.aidoc.common.Result;
import org.springframework.http.ResponseEntity;

/**
 * SSE认证工具类
 */
public class SseAuthUtil {

    /**
     * 验证用户是否登录
     * @return Result对象，成功时返回null，失败时返回错误Result
     */
    public static Result<Void> checkLogin() {
        if (!StpUtil.isLogin()) {
            return Result.error(401, "用户未登录");
        }
        return null;
    }

    /**
     * 获取当前用户ID
     * @return 用户ID
     */
    public static Long getCurrentUserId() {
        return Long.parseLong(StpUtil.getLoginIdAsString());
    }

    /**
     * 创建未登录响应
     * @return 401响应
     */
    public static ResponseEntity<Result<Void>> unauthorizedResponse() {
        return ResponseEntity.status(401).body(Result.error(401, "用户未登录"));
    }
}