package com.planit.analytics.entity;

import com.planit.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * user_action_logs 테이블
 * 
 * 사용자의 할 일 완료/미루기/삭제 행동을 기록
 * 배치 분석 및 AI 리포트 생성에 사용
 */
@Entity
@Table(
    name = "user_action_logs",
    indexes = {
        // 배치 성능 최적화: 사용자별 시간순 조회
        @Index(name = "idx_user_action_logs_user_time", columnList = "user_id, action_time"),
        // 배치 성능 최적화: 목표별 집계
        @Index(name = "idx_user_action_logs_topic", columnList = "goals_id, action_time")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActionLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false, length = 255)
    private String userId;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private Long goalsId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Column(nullable = false)
    private LocalDateTime actionTime;

    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalDate postponedToDate;

    @Column(length = 10)
    private String dayOfWeek;

    private Integer hourOfDay;

    /**
     * 행동 타입
     */
    public enum ActionType {
        COMPLETED,   // 완료
        POSTPONED,   // 미루기
        DELETED      // 삭제
    }
}
