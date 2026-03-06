/**
 * Action Log DTO
 * Task Service와 Insight Service 간 통신에 사용되는 데이터 전송 객체
 * @since 2026-03-03
 */
package com.planit.analytics.dto;

import com.planit.analytics.entity.ActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionLogDto {
    private String userId;
    private Long taskId;
    private Long goalsId;
    private ActionType actionType;
    private LocalDateTime actionTime;
    private LocalDate dueDate;
    private LocalDate postponedToDate;
}
