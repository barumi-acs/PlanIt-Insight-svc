/**
 * DynamoDB Repository
 * AI 리포트를 DynamoDB에 저장/조회하는 Repository
 * @since 2026-03-03
 */
package com.planit.analytics.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DynamoDBRepository {
    
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    
    @Value("${aws.dynamodb.table-name}")
    private String tableName;
    
    /**
     * AI 리포트를 DynamoDB에 저장
     * PK: USER#{userId}
     * SK: REPORT#{yearMonth}#{reportType}
     */
    public void saveReport(String userId, String yearMonth, String reportType, Map<String, Object> reportData) {
        try {
            String pk = "USER#" + userId;
            String sk = "REPORT#" + yearMonth + "#" + reportType;
            String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String reportDataJson = objectMapper.writeValueAsString(reportData);
            
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", AttributeValue.builder().s(pk).build());
            item.put("SK", AttributeValue.builder().s(sk).build());
            item.put("created_at", AttributeValue.builder().s(createdAt).build());
            item.put("report_data", AttributeValue.builder().s(reportDataJson).build());
            
            PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();
            
            dynamoDbClient.putItem(request);
            log.info("Successfully saved report to DynamoDB: PK={}, SK={}", pk, sk);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize report data", e);
            throw new CustomException(ErrorCode.IS5002);
        } catch (DynamoDbException e) {
            log.error("Failed to save report to DynamoDB", e);
            throw new CustomException(ErrorCode.IS5002);
        }
    }
    
    /**
     * DynamoDB에서 AI 리포트 조회
     */
    public Map<String, Object> getReport(String userId, String yearMonth, String reportType) {
        try {
            String pk = "USER#" + userId;
            String sk = "REPORT#" + yearMonth + "#" + reportType;
            
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s(pk).build());
            key.put("SK", AttributeValue.builder().s(sk).build());
            
            GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();
            
            GetItemResponse response = dynamoDbClient.getItem(request);
            
            if (!response.hasItem()) {
                log.warn("Report not found in DynamoDB: PK={}, SK={}", pk, sk);
                return null;
            }
            
            String reportDataJson = response.item().get("report_data").s();
            Map<String, Object> reportData = objectMapper.readValue(reportDataJson, Map.class);
            
            log.info("Successfully retrieved report from DynamoDB: PK={}, SK={}", pk, sk);
            return reportData;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize report data", e);
            throw new CustomException(ErrorCode.IS5001);
        } catch (DynamoDbException e) {
            log.error("Failed to get report from DynamoDB", e);
            throw new CustomException(ErrorCode.IS5001);
        }
    }
}
