/**
 * REST API Adapter (Phase 1)
 * Service B(Python)를 REST API로 동기 호출하는 Adapter 구현체
 * 
 * Phase 2에서는 SqsAdapter로 교체 예정
 * @since 2026-03-03
 */
package com.planit.analytics.adapter;

import com.planit.analytics.dto.AIReportRequest;
import com.planit.analytics.dto.AIReportResponse;
import com.planit.analytics.port.AIReportPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestApiAdapter implements AIReportPort {
    
    private final RestTemplate restTemplate;
    
    @Value("${service-b.base-url:http://localhost:8000}")
    private String serviceBUrl;
    
    @Override
    public AIReportResponse generateReport(AIReportRequest request) {
        log.info("Calling Service B via REST API: {} for user: {}, reportType: {}", 
                serviceBUrl, request.getUserId(), request.getReportType());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<AIReportRequest> entity = new HttpEntity<>(request, headers);
            
            // Service B의 AI 리포트 생성 API 호출
            AIReportResponse response = restTemplate.postForObject(
                serviceBUrl + "/ai/reports/generate",
                entity,
                AIReportResponse.class
            );
            
            if (response != null && response.isSuccess()) {
                log.info("Successfully generated AI report for user: {}", request.getUserId());
            } else {
                log.warn("AI report generation returned unsuccessful response for user: {}", request.getUserId());
            }
            
            return response;
            
        } catch (RestClientException e) {
            log.error("Failed to call Service B for user: {}, error: {}", 
                    request.getUserId(), e.getMessage(), e);
            
            // 실패 시 기본 응답 반환
            return AIReportResponse.builder()
                .success(false)
                .errorMessage("Service B 호출 실패: " + e.getMessage())
                .reportData(new HashMap<>())
                .build();
        }
    }
}
