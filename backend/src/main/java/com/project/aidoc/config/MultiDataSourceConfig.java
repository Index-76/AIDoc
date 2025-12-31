package com.project.aidoc.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.sql.DataSource;

/**
 * 多数据源配置类
 * 配置 MongoDB 和 MyBatis 数据源
 */
@Configuration
@MapperScan(basePackages = "com.project.aidoc.mapper", sqlSessionFactoryRef = "mysqlSqlSessionFactory")
public class MultiDataSourceConfig {

    @Autowired
    private DataSource mysqlDataSource;

    /**
     * 配置 MyBatis 的 SqlSessionFactory
     */
    @Bean(name = "mysqlSqlSessionFactory")
    @Primary
    public SqlSessionFactory mysqlSqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(mysqlDataSource);
        
        // 设置mapper位置，如果不存在mapper XML文件，可以不设置或设置为一个存在的路径
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            // 尝试获取资源，如果路径不存在也不会抛出异常，而是返回空数组
            factoryBean.setMapperLocations(resolver.getResources("classpath:mapper/*.xml"));
        } catch (Exception e) {
            // 如果mapper路径不存在，继续执行，不设置mapperLocations
            System.out.println("No mapper XML files found, proceeding without XML mappers");
        }
        
        return factoryBean.getObject();
    }

    /**
     * 配置 MongoDB 的 MongoTemplate
     */
    @Bean(name = "mongoTemplate")
    public MongoTemplate mongoTemplate() {
        MongoClient mongoClient = MongoClients.create(
            "mongodb://" + 
            System.getProperty("spring.data.mongodb.host", "localhost") + ":" + 
            System.getProperty("spring.data.mongodb.port", "27017")
        );
        String database = System.getProperty("spring.data.mongodb.database", "aidoc_dev");
        return new MongoTemplate(mongoClient, database);
    }
}