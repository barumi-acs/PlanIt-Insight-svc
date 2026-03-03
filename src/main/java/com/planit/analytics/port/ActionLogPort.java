/**
 * Action Log Port (Hexagonal Architecture)
 * Task Service에서 Insight Service로 로그를 전송하기 위한 인터페이스
 * 
 * Phase 1: REST API 동기 호출 (RestApiAdapter)
 * Phase 2: SQS/Kafka 비동기 메시지 발행 (SQSAdapter)
 * 
 * 비즈니스 로직은 이 인터페이스에만 의존하며, 구현체 교체로 통신 방식 전환 가능
 * @since 2026-03-03
 */
package com.planit.analytics.port;

import com.planit.analytics.dto.ActionLogDto;

public interface ActionLogPort {
    
    /**
     * Action Log를 전송합니다.
     * 통신 방식(동기/비동기)에 독립적인 추상화된 메서드
     * 
     * @param actionLog 전송할 Action Log 데이터
     */
    void sendActionLog(ActionLogDto actionLog);
}
