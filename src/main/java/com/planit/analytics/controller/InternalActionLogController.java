/**
 * Internal Action Log Controller
 * Task Service에서 호출하는 내부 API
 * 사용자 행동 로그를 저장
 * @since 2026-03-03
 */
package com.planit.analytics.controller;

import com.planit.analytics.dto.ActionLogDto;
import com.planit.analytics.entity.ActionLogEntity;
import com.planit.analytics.service.ActionLogService;
import com.planit.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/internal/api/v1/action-logs")
@RequiredArgsConstructor
@Tag(name = "Internal Action Log", description = "내부 서비스 간 통신용 Action Log API")
public class InternalActionLogController {
    
    private final ActionLogService actionLogService;
    
    /**
     * Action Log 저장
     * Task Service에서 할 일 완료/미루기 처리 시 호출
     */
    @PostMapping
    @Operation(
        summary = "Action Log 저장",
        description = "Task Service에서 사용자의 할 일 처리 로그를 전송받아 저장합니다. 내부 서비스 간 통신용 API입니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Action Log 저장 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 데이터"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "서버 내부 에러"
        )
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveActionLog(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Action Log 데이터",
            required = true,
            content = @Content(schema = @Schema(implementation = ActionLogDto.class))
        )
        @Valid @RequestBody ActionLogDto actionLogDto
    ) {
        log.info("POST /internal/api/v1/action-logs - userId: {}, taskId: {}, actionType: {}", 
                actionLogDto.getUserId(), actionLogDto.getTaskId(), actionLogDto.getActionType());
        
        ActionLogEntity savedEntity = actionLogService.saveActionLog(actionLogDto);
        
        Map<String, Object> result = new HashMap<>();
        result.put("logId", savedEntity.getLogId());
        result.put("userId", savedEntity.getUserId());
        result.put("taskId", savedEntity.getTaskId());
        result.put("actionType", savedEntity.getActionType());
        result.put("createdAt", savedEntity.getCreatedAt());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
