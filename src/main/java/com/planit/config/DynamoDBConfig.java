/**
 * DynamoDB 설정
 * AWS SDK v2를 사용한 DynamoDB 클라이언트 설정
 * IAM Role 기반 인증 사용
 * @since 2026-03-03
 */
package com.planit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDBConfig {
    
    @Value("${aws.dynamodb.region:us-east-1}")
    private String region;
    
    /**
     * DynamoDB 클라이언트 빈 생성
     * IAM Role 기반 인증 (DefaultCredentialsProvider)
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
    
    /**
     * JSON 직렬화/역직렬화를 위한 ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
