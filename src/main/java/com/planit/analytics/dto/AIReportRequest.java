/**
 * AI Report 생성 요청 DTO
 * Service A에서 Service B(Python)로 리포트 생성을 요청할 때 사용
 * @since 2026-03-03
 */
package com.planit.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIReportRequest {
    
    /**
     * 사용자 ID
     */
    private String userId;
    
    /**
     * 리포트 타입 (예: "GROWTH", "TIMELINE", "PATTERN", "SUMMARY")
     */
    private String reportType;
    
    /**
     * 대상 기간 (예: "2026-02")
     */
    private String targetPeriod;
    
    /**
     * 통계 데이터 (AI 프롬프트에 주입될 컨텍스트)
     * 예: {"growthRate": 24, "topicName": "운동", "previousRate": 45}
     */
    private Map<String, Object> statisticsData;
}
