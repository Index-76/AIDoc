package com.project.aidoc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 从classpath:/static/提供静态资源（已包含前端构建结果）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 将根路径映射到index.html
        registry.addViewController("/").setViewName("forward:/index.html");
        
        // 为Flutter路由处理添加其他常见路径
        // 但排除API路径和静态资源路径
        registry.addViewController("/app").setViewName("forward:/index.html");
        registry.addViewController("/dashboard").setViewName("forward:/index.html");
        registry.addViewController("/settings").setViewName("forward:/index.html");
        registry.addViewController("/docs").setViewName("forward:/index.html");
        registry.addViewController("/profile").setViewName("forward:/index.html");
    }
}