package com.project.aidoc.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MongoDB 数据源配置类
 */
@Configuration
public class DataSourceConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongodbUri;

    /**
     * 配置 MongoDB 的 MongoClient
     */
    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongodbUri);
    }

    /**
     * 配置 MongoDB 的 MongoTemplate
     */
    @Bean(name = "mongoTemplate")
    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
        // 从 URI 中提取数据库名称
        String database = extractDatabaseName(mongodbUri);
        return new MongoTemplate(mongoClient, database);
    }

    /**
     * 从 MongoDB URI 中提取数据库名称
     */
    private String extractDatabaseName(String uri) {
        // MongoDB URI 格式：mongodb://host:port/database?options
        if (uri != null && uri.contains("/")) {
            String dbPart = uri.substring(uri.lastIndexOf("/") + 1);
            if (dbPart.contains("?")) {
                return dbPart.substring(0, dbPart.indexOf("?"));
            }
            return dbPart;
        }
        return "aidoc_dev"; // 默认数据库名
    }
}
