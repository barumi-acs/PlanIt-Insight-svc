/**
 * Feedback Controller
 * 사용자 피드백 조회 API
 * 
 * @since 2026-03-03
 * @updated 2026-03-09 (JWT 인증 적용)
 */
package com.planit.analytics.controller;

import com.planit.analytics.service.FeedbackService;
import com.planit.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Feedback", description = "사용자 피드백 조회 API")
public class FeedbackController {
    
    private final FeedbackService feedbackService;
    
    /**
     * 일간 응원 피드백 조회
     * 홈 화면 상단에 띄울 오늘의 요일별 AI 응원 메시지 조회
     * 
     * @param userId JWT 토큰에서 추출된 사용자 ID (자동 주입)
     */
    @GetMapping("/daily-cheer")
    @Operation(
        summary = "일간 응원 피드백 조회",
        description = "홈 화면에 표시할 오늘의 요일별 응원 메시지를 조회합니다. 최근 3개월 데이터를 기반으로 평균 대비 수행률 차이를 계산합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "일간 응원 피드백 조회 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 파라미터"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자"
        )
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDailyCheer(
            @org.springframework.security.core.annotation.AuthenticationPrincipal String userId
    ) {
        log.info("GET /api/v1/feedbacks/daily-cheer - userId: {}", userId);
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = feedbackService.getDailyCheer(userId);
        long duration = System.currentTimeMillis() - startTime;
        
        if (duration > 500) {
            log.warn("Daily cheer API response time exceeded 500ms: {}ms", duration);
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * AI 피드백 대시보드 전체 조회
     * 리포트 탭에서 성장 격려, 타임라인, 미룸 패턴, 종합 피드백 4가지를 한 번에 조회
     * 
     * @param userId JWT 토큰에서 추출된 사용자 ID (자동 주입)
     * @param yearMonth 대상 월 (예: 2026-02)
     * @param week 대상 주차
     */
    @GetMapping("/dashboard")
    @Operation(
        summary = "AI 피드백 대시보드 조회",
        description = "리포트 탭에서 표시할 4가지 피드백(성장 격려, 타임라인, 미룸 패턴, 종합 피드백)을 한 번에 조회합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "피드백 대시보드 조회 성공",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 파라미터"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "분석할 통계 데이터 부족 (IS4041)"
        )
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboard(
        @org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
        @Parameter(description = "대상 월 (예: 2026-02)", required = true, example = "2026-02")
        @RequestParam String yearMonth,
        
        @Parameter(description = "대상 주차", required = true, example = "9")
        @RequestParam Integer week
    ) {
        log.info("GET /api/v1/feedbacks/dashboard - userId: {}, yearMonth: {}, week: {}", 
                userId, yearMonth, week);
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = feedbackService.getDashboard(userId, yearMonth, week);
        long duration = System.currentTimeMillis() - startTime;
        
        if (duration > 500) {
            log.warn("Dashboard API response time exceeded 500ms: {}ms", duration);
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
