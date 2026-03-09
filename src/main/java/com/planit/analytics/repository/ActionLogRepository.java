/**
 * Action Log Repository
 * 사용자 행동 로그 조회를 위한 Repository
 * @since 2026-03-03
 */
package com.planit.analytics.repository;

import com.planit.analytics.dto.DayOfWeekStats;
import com.planit.analytics.entity.ActionLogEntity;
import com.planit.analytics.entity.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActionLogRepository extends JpaRepository<ActionLogEntity, Long> {
    
    /**
     * 특정 사용자의 날짜 범위 내 로그 조회
     * 복합 인덱스 (user_id, action_time) 활용
     */
    List<ActionLogEntity> findByUserIdAndActionTimeBetween(
        String userId, 
        LocalDateTime startTime, 
        LocalDateTime endTime
    );
    
    /**
     * 특정 사용자의 주제별, 액션 타입별 로그 조회
     * 복합 인덱스 (user_id, goals_id, action_type) 활용
     */
    List<ActionLogEntity> findByUserIdAndGoalsIdAndActionType(
        String userId, 
        Long goalsId, 
        ActionType actionType
    );
    
    /**
     * 요일별 통계 조회
     * 완료율 = (완료 건수 / 전체 건수) * 100
     */
    @Query("""
        SELECT 
            a.dayOfWeek as dayOfWeek,
            COUNT(a) as totalCount,
            SUM(CASE WHEN a.actionType = 'COMPLETED' THEN 1 ELSE 0 END) as completedCount,
            (SUM(CASE WHEN a.actionType = 'COMPLETED' THEN 1 ELSE 0 END) * 100.0 / COUNT(a)) as completionRate
        FROM ActionLogEntity a
        WHERE a.userId = :userId
        AND a.actionTime BETWEEN :startTime AND :endTime
        GROUP BY a.dayOfWeek
        """)
    List<DayOfWeekStats> calculateDayOfWeekStats(
        @Param("userId") String userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * 특정 기간 내 평균 일일 Task 수 계산
     */
    @Query("""
        SELECT COUNT(DISTINCT a.taskId) / :days
        FROM ActionLogEntity a
        WHERE a.userId = :userId
        AND a.actionTime BETWEEN :startTime AND :endTime
        """)
    Double calculateAverageDailyTaskCount(
        @Param("userId") String userId,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("days") Long days
    );
    
    /**
     * 특정 기간 내 미룸 횟수 조회
     */
    Long countByUserIdAndActionTypeAndActionTimeBetween(
        String userId,
        ActionType actionType,
        LocalDateTime startTime,
        LocalDateTime endTime
    );
    
    /**
     * 특정 기간 내 활동한 모든 유니크한 사용자 ID 조회
     * 배치 작업에서 활성 사용자 목록을 가져올 때 사용
     */
    @Query("""
        SELECT DISTINCT a.userId
        FROM ActionLogEntity a
        WHERE a.actionTime BETWEEN :startTime AND :endTime
        """)
    List<String> findDistinctUserIdsByActionTimeBetween(
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
}
