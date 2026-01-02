package com.project.aidoc.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sa-Token 安全配置
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> StpUtil.checkLogin()))
                .addPathPatterns("/api/v1/user/**", "/api/v1/protected/**", "/api/v1/users/me")
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
                        "/config/**",
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

        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                String path = request.getRequestURI();

                if (StpUtil.isLogin() &&
                    (path.contains("/api/v1/auth/login") || path.contains("/api/v1/auth/register"))) {

                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"msg\":\"已登录用户不能访问此页面\",\"data\":null}");
                    return false;
                }

                return true;
            }
        });
    }
}