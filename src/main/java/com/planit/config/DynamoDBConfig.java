/**
 * DynamoDB 설정
 * AWS SDK v2를 사용한 DynamoDB 클라이언트 설정
 * IAM Role 기반 인증 사용
 * @since 2026-03-03
 */
package com.planit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDBConfig {

    @Value("${aws.dynamodb.region:us-east-1}")
    private String region;

    @Value("${aws.dynamodb.endpoint:}")
    private String endpoint;

    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    /**
     * DynamoDB 클라이언트 빈 생성
     * endpoint가 설정되어 있으면 로컬 DynamoDB 사용
     * 없으면 AWS 클라우드 DynamoDB 사용 (IAM Role 기반 인증)
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(Region.of(region));

        // 자격증명 설정: accessKeyId가 있으면 Static, 없으면 DefaultCredentialsProvider(IAM
        // Role/CLI)
        if (accessKeyId != null && !accessKeyId.isEmpty()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        // 로컬 DynamoDB endpoint 설정 (개발 환경)
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(endpoint));
        }

        return builder.build();
    }

    /**
     * JSON 직렬화/역직렬화를 위한 ObjectMapper
     * Java 8 날짜/시간 타입(LocalDateTime 등) 지원
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Java 8 날짜/시간 모듈 등록
        mapper.registerModule(new JavaTimeModule());
        // 날짜를 타임스탬프가 아닌 ISO-8601 형식으로 직렬화
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
