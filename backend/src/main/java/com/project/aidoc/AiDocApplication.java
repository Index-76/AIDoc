package com.project.aidoc;

import com.project.aidoc.config.ApiProperties;
import com.project.aidoc.config.SystemConfigManager;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.project.aidoc.mapper")
@EnableConfigurationProperties({ApiProperties.class})
public class AiDocApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDocApplication.class, args);
    }
    
    @Bean
    public CommandLineRunner commandLineRunner(SystemConfigManager systemConfigManager) {
        return args -> {
            // 移除初始化文件配置的逻辑，因为我们现在使用MongoDB存储配置
        };
    }

}