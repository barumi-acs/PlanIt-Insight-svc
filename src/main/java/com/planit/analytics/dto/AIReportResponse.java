/**
 * AI Report 생성 응답 DTO
 * Service B(Python)에서 생성된 AI 리포트 결과를 받을 때 사용
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
public class AIReportResponse {
    
    /**
     * 생성 성공 여부
     */
    private boolean success;
    
    /**
     * 생성된 리포트 데이터 (JSON 형태)
     * 예: {"message": "이전 3개월 보다 운동 분야에서 24% 성장했어요!", "growthRate": 24}
     */
    private Map<String, Object> reportData;
    
    /**
     * 에러 메시지 (실패 시)
     */
    private String errorMessage;
}
