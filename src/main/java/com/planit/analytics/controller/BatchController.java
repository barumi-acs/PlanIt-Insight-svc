/**
 * Batch Controller
 * 배치 작업을 수동으로 트리거하는 API (개발/테스트용)
 * @since 2026-03-07
 */
package com.planit.analytics.controller;

import com.planit.analytics.scheduler.ReportGenerationScheduler;
import com.planit.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
@Tag(name = "Batch", description = "배치 작업 수동 실행 API (개발/테스트용)")
public class BatchController {
    
    private final ReportGenerationScheduler scheduler;
    
    /**
     * 주간 리포트 생성 배치 수동 실행
     */
    @PostMapping("/weekly-reports")
    @Operation(
        summary = "주간 리포트 생성 배치 수동 실행",
        description = "스케줄러를 기다리지 않고 즉시 주간 리포트를 생성합니다."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerWeeklyReports() {
        log.info("Manual trigger: weekly report generation");
        
        try {
            scheduler.generateWeeklyReports();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "주간 리포트 생성 배치가 실행되었습니다.");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Failed to trigger weekly reports", e);
            Map<String, Object> result = new HashMap<>();
            result.put("message", "배치 실행 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(result));
        }
    }
    
    /**
     * 월간 리포트 생성 배치 수동 실행
     */
    @PostMapping("/monthly-reports")
    @Operation(
        summary = "월간 리포트 생성 배치 수동 실행",
        description = "스케줄러를 기다리지 않고 즉시 월간 리포트를 생성합니다."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerMonthlyReports() {
        log.info("Manual trigger: monthly report generation");
        
        try {
            scheduler.generateMonthlyReports();
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "월간 리포트 생성 배치가 실행되었습니다.");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Failed to trigger monthly reports", e);
            Map<String, Object> result = new HashMap<>();
            result.put("message", "배치 실행 중 오류가 발생했습니다: " + e.getMessage());
            return ResponseEntity.ok(ApiResponse.success(result));
        }
    }
    
    /**
     * 특정 사용자의 리포트 생성 (테스트용)
     */
    @PostMapping("/generate-report")
    @Operation(
        summary = "특정 사용자 리포트 생성",
        description = "특정 사용자의 리포트를 즉시 생성합니다 (테스트용)."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateReportForUser(
        @RequestParam String userId,
        @RequestParam(required = false) String yearMonth
    ) {
        log.info("Manual trigger: generate report for user: {}, yearMonth: {}", userId, yearMonth);
        
        try {
            YearMonth targetMonth = yearMonth != null ? 
                YearMonth.parse(yearMonth) : YearMonth.now();
            
            // 월간 리포트 생성 (GROWTH, TIMELINE, PATTERN, SUMMARY 모두 생성)
            scheduler.generateMonthlyReportForUser(userId, targetMonth);
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "리포트 생성이 완료되었습니다.");
            result.put("userId", userId);
            result.put("yearMonth", targetMonth.toString());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Failed to generate report for user: {}", userId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("message", "리포트 생성 중 오류가 발생했습니다: " + e.getMessage());
            result.put("userId", userId);
            return ResponseEntity.ok(ApiResponse.success(result));
        }
    }
}
