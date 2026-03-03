/**
 * Action Log Entity
 * 사용자의 할 일 처리 로그를 저장하는 엔티티
 * Soft Delete 지원
 * @since 2026-03-03
 */
package com.planit.analytics.entity;

import com.planit.global.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_action_logs", indexes = {
    @Index(name = "idx_user_action_logs_user_time", columnList = "user_id, action_time"),
    @Index(name = "idx_user_action_logs_topic", columnList = "user_id, goals_id, action_type")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SQLDelete(sql = "UPDATE user_action_logs SET deleted_at = NOW() WHERE log_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ActionLogEntity extends BaseTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;
    
    @Column(nullable = false, length = 36)
    private String userId;
    
    @Column(nullable = false)
    private Long taskId;
    
    @Column
    private Long goalsId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionType actionType;
    
    @Column(nullable = false)
    private LocalDateTime actionTime;
    
    @Column(nullable = false)
    private LocalDate dueDate;
    
    @Column
    private LocalDate postponedToDate;
    
    @Column(nullable = false, length = 10)
    private String dayOfWeek;
    
    @Column(nullable = false)
    private Integer hourOfDay;
    
    @Column
    private LocalDateTime deletedAt;
    
    /**
     * 엔티티 저장 전 자동으로 요일과 시간 정보를 계산
     */
    @PrePersist
    public void prePersist() {
        if (actionTime != null) {
            this.dayOfWeek = actionTime.getDayOfWeek().name();
            this.hourOfDay = actionTime.getHour();
        }
    }
}
