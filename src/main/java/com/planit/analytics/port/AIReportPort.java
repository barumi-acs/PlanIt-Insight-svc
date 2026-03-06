/**
 * AI Report Port (Hexagonal Architecture)
 * Service A에서 Service B(Python)로 AI 리포트 생성을 요청하기 위한 인터페이스
 * 
 * Phase 1: REST API 동기 호출 (RestApiAdapter)
 * Phase 2: 비동기 메시지 기반 처리 (SQSAdapter 등)
 * 
 * 비즈니스 로직은 이 인터페이스에만 의존하며, 구현체 교체로 통신 방식 전환 가능
 * @since 2026-03-03
 */
package com.planit.analytics.port;

import com.planit.analytics.dto.AIReportRequest;
import com.planit.analytics.dto.AIReportResponse;

public interface AIReportPort {
    
    /**
     * AI 리포트 생성을 요청합니다.
     * 통신 방식(동기/비동기)에 독립적인 추상화된 메서드
     * 
     * @param request AI 리포트 생성 요청 데이터
     * @return AI 리포트 생성 결과
     */
    AIReportResponse generateReport(AIReportRequest request);
}
