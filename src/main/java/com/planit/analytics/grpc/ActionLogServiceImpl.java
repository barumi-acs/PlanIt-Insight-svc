package com.planit.analytics.grpc;

import com.planit.analytics.entity.UserActionLog;
import com.planit.analytics.repository.UserActionLogRepository;
import com.planit.grpc.actionlog.ActionLogRequest;
import com.planit.grpc.actionlog.ActionLogResponse;
import com.planit.grpc.actionlog.ActionLogServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Action Log gRPC Server
 * 
 * Schedule Service로부터 사용자 행동 로그를 수신하여 DB에 저장
 * 
 * 핵심 기능:
 * 1. action_time 파싱
 * 2. day_of_week, hour_of_day 계산 (배치 성능 최적화)
 * 3. user_action_logs 테이블에 INSERT
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ActionLogServiceImpl extends ActionLogServiceGrpc.ActionLogServiceImplBase {

    private final UserActionLogRepository actionLogRepository;

    @Override
    public void recordActionLog(ActionLogRequest request, StreamObserver<ActionLogResponse> responseObserver) {
        log.info("[gRPC ActionLog] Received: user={}, task={}, goals={}, action={}, time={}",
                request.getUserId(),
                request.getTaskId(),
                request.getGoalsId(),
                request.getActionType(),
                request.getActionTime());

        try {
            // 1️⃣ action_time 파싱 (ISO 8601: 2026-03-06T12:30:00)
            LocalDateTime actionTime = LocalDateTime.parse(
                    request.getActionTime(),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );

            // 2️⃣ day_of_week 계산 (MONDAY, TUESDAY, ...)
            DayOfWeek dayOfWeek = actionTime.getDayOfWeek();
            String dayOfWeekStr = dayOfWeek.toString(); // "MONDAY"

            // 3️⃣ hour_of_day 계산 (0-23)
            int hourOfDay = actionTime.getHour();

            // 4️⃣ due_date 파싱 (YYYY-MM-DD)
            LocalDate dueDate = LocalDate.parse(request.getDueDate());

            // 5️⃣ postponed_to_date 파싱 (POSTPONED일 때만)
            LocalDate postponedToDate = null;
            if (!request.getPostponedToDate().isEmpty()) {
                postponedToDate = LocalDate.parse(request.getPostponedToDate());
            }

            // 6️⃣ Entity 생성
            UserActionLog actionLog = UserActionLog.builder()
                    .userId(request.getUserId())
                    .taskId(request.getTaskId())
                    .goalsId(request.getGoalsId())
                    .actionType(UserActionLog.ActionType.valueOf(request.getActionType()))
                    .actionTime(actionTime)
                    .dueDate(dueDate)
                    .postponedToDate(postponedToDate)
                    .dayOfWeek(dayOfWeekStr)
                    .hourOfDay(hourOfDay)
                    .build();

            // 7️⃣ DB 저장
            UserActionLog saved = actionLogRepository.save(actionLog);

            // 8️⃣ 성공 응답
            ActionLogResponse response = ActionLogResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Action log recorded successfully")
                    .setLogId(saved.getLogId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("[gRPC ActionLog] Successfully saved: log_id={}, day_of_week={}, hour_of_day={}",
                    saved.getLogId(), dayOfWeekStr, hourOfDay);

        } catch (Exception e) {
            // 9️⃣ 예외 발생 시에도 성공 응답 (Schedule Service 롤백 방지)
            log.error("[gRPC ActionLog] Failed to save action log, but returning success to prevent rollback", e);

            ActionLogResponse response = ActionLogResponse.newBuilder()
                    .setSuccess(true) // ⚠️ 의도적으로 true 반환
                    .setMessage("Action log received (save failed internally)")
                    .setLogId(0L)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
