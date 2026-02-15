package com.project.aidoc;

import com.project.aidoc.config.ApiProperties;
import com.project.aidoc.config.SystemConfigManager;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.project.aidoc.mapper")
@EnableConfigurationProperties({ApiProperties.class})
@EnableAsync
public class AiDocApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDocApplication.class, args);
    }
    
    @Bean
    public CommandLineRunner commandLineRunner(SystemConfigManager systemConfigManager) {
        return args -> {
            
        };
    }

}