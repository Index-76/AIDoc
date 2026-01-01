package com.project.aidoc.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 安全配置
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，校验规则为：所有以 '/api/v1/' 开头的路径都需要登录验证
        registry.addInterceptor(new SaInterceptor(handler -> StpUtil.checkLogin()))
                .addPathPatterns("/api/v1/**") // 对所有API接口进行登录验证
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/register",
                        "/api/v1/health",
                        "/error",
                        "/",
                        "/index.html",
                        "/static/**",
                        "/assets/**",
                        "/config/**", // 允许访问配置文件
                        "/**/*.js",
                        "/**/*.css",
                        "/**/*.png",
                        "/**/*.jpg",
                        "/**/*.jpeg",
                        "/**/*.gif",
                        "/**/*.ico",
                        "/**/*.woff",
                        "/**/*.woff2",
                        "/**/*.ttf",
                        "/webjars/**");
    }
}