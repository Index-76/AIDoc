package com.project.aidoc;

import com.project.aidoc.config.ApiProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.project.aidoc.mapper")
@EnableConfigurationProperties({ApiProperties.class})
public class AiDocApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDocApplication.class, args);
    }

}