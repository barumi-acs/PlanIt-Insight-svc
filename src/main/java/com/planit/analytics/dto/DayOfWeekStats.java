/**
 * Day of Week Statistics DTO
 * 요일별 통계 조회 결과를 담는 인터페이스
 * @since 2026-03-03
 */
package com.planit.analytics.dto;

public interface DayOfWeekStats {
    String getDayOfWeek();
    Long getTotalCount();
    Long getCompletedCount();
    Double getCompletionRate();
}
