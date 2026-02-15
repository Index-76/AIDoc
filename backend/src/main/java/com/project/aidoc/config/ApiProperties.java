package com.project.aidoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * API配置属性类
 */
@Component
@ConfigurationProperties(prefix = "api")
@Data
public class ApiProperties {
    private String prefix;
}