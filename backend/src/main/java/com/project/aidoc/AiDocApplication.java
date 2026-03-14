package com.project.aidoc;

import com.project.aidoc.config.ApiProperties;
import com.project.aidoc.config.SystemConfigManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
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
