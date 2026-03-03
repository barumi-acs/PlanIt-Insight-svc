/**
 * Action Log Service
 * Task Service에서 전송된 사용자 행동 로그를 검증하고 저장하는 서비스
 * @since 2026-03-03
 */
package com.planit.analytics.service;

import com.planit.analytics.dto.ActionLogDto;
import com.planit.analytics.entity.ActionLogEntity;
import com.planit.analytics.entity.ActionType;
import com.planit.analytics.repository.ActionLogRepository;
import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionLogService {
    
    private final ActionLogRepository actionLogRepository;
    
    /**
     * Action Log 저장
     * 데이터 검증 후 ActionLogEntity로 변환하여 저장
     * 
     * @param actionLogDto 저장할 Action Log 데이터
     * @return 저장된 ActionLogEntity
     */
    @Transactional
    public ActionLogEntity saveActionLog(ActionLogDto actionLogDto) {
        log.info("Saving action log: userId={}, taskId={}, actionType={}", 
                actionLogDto.getUserId(), actionLogDto.getTaskId(), actionLogDto.getActionType());
        
        // 데이터 검증
        validateActionLog(actionLogDto);
        
        // DTO를 Entity로 변환
        ActionLogEntity entity = convertToEntity(actionLogDto);
        
        // 저장
        ActionLogEntity savedEntity = actionLogRepository.save(entity);
        
        log.info("Action log saved successfully: logId={}", savedEntity.getLogId());
        return savedEntity;
    }
    
    /**
     * Action Log 데이터 검증
     * 필수 필드 및 비즈니스 규칙 검증
     */
    private void validateActionLog(ActionLogDto dto) {
        // 필수 필드 검증
        if (dto.getUserId() == null || dto.getUserId().trim().isEmpty()) {
            log.error("Validation failed: userId is null or empty");
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (dto.getTaskId() == null) {
            log.error("Validation failed: taskId is null");
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (dto.getActionType() == null) {
            log.error("Validation failed: actionType is null");
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (dto.getActionTime() == null) {
            log.error("Validation failed: actionTime is null");
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (dto.getDueDate() == null) {
            log.error("Validation failed: dueDate is null");
            throw new CustomException(ErrorCode.C4001);
        }
        
        // ActionType 유효성 검증
        if (dto.getActionType() != ActionType.COMPLETED && 
            dto.getActionType() != ActionType.POSTPONED) {
            log.error("Validation failed: invalid actionType={}", dto.getActionType());
            throw new CustomException(ErrorCode.C4001);
        }
        
        // POSTPONED인 경우 postponedToDate 필수
        if (dto.getActionType() == ActionType.POSTPONED && dto.getPostponedToDate() == null) {
            log.error("Validation failed: postponedToDate is required for POSTPONED action");
            throw new CustomException(ErrorCode.C4001);
        }
        
        // actionTime이 미래 시간이 아닌지 검증
        if (dto.getActionTime().isAfter(LocalDateTime.now())) {
            log.error("Validation failed: actionTime cannot be in the future");
            throw new CustomException(ErrorCode.C4001);
        }
        
        // userId 길이 검증 (최대 36자 - UUID 길이)
        if (dto.getUserId().length() > 36) {
            log.error("Validation failed: userId length exceeds 36 characters");
            throw new CustomException(ErrorCode.C4001);
        }
        
        log.debug("Action log validation passed");
    }
    
    /**
     * DTO를 Entity로 변환
     */
    private ActionLogEntity convertToEntity(ActionLogDto dto) {
        return ActionLogEntity.builder()
                .userId(dto.getUserId())
                .taskId(dto.getTaskId())
                .goalsId(dto.getGoalsId())
                .actionType(dto.getActionType())
                .actionTime(dto.getActionTime())
                .dueDate(dto.getDueDate())
                .postponedToDate(dto.getPostponedToDate())
                .build();
        // dayOfWeek와 hourOfDay는 @PrePersist에서 자동 계산됨
    }
    
    /**
     * 특정 사용자의 Action Log 개수 조회
     * 
     * @param userId 사용자 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return Action Log 개수
     */
    @Transactional(readOnly = true)
    public long countActionLogs(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("Counting action logs: userId={}, startTime={}, endTime={}", 
                userId, startTime, endTime);
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (startTime == null || endTime == null) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (startTime.isAfter(endTime)) {
            log.error("Validation failed: startTime is after endTime");
            throw new CustomException(ErrorCode.C4001);
        }
        
        return actionLogRepository.findByUserIdAndActionTimeBetween(userId, startTime, endTime)
                .size();
    }
    
    /**
     * 특정 사용자의 완료율 조회
     * 
     * @param userId 사용자 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 완료율 (0-100)
     */
    @Transactional(readOnly = true)
    public double getCompletionRate(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("Calculating completion rate: userId={}, startTime={}, endTime={}", 
                userId, startTime, endTime);
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (startTime == null || endTime == null) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (startTime.isAfter(endTime)) {
            log.error("Validation failed: startTime is after endTime");
            throw new CustomException(ErrorCode.C4001);
        }
        
        var logs = actionLogRepository.findByUserIdAndActionTimeBetween(userId, startTime, endTime);
        
        if (logs.isEmpty()) {
            return 0.0;
        }
        
        long completedCount = logs.stream()
                .filter(log -> log.getActionType() == ActionType.COMPLETED)
                .count();
        
        return (completedCount * 100.0) / logs.size();
    }
}
