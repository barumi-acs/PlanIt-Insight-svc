/**
 * Report Generation Scheduler
 * 주기적으로 사용자별 AI 리포트를 생성하는 배치 스케줄러
 * ShedLock을 사용하여 분산 환경에서 중복 실행 방지
 * @since 2026-03-03
 */
package com.planit.analytics.scheduler;

import com.planit.analytics.dto.AIReportRequest;
import com.planit.analytics.dto.AIReportResponse;
import com.planit.analytics.port.AIReportPort;
import com.planit.analytics.repository.ActionLogRepository;
import com.planit.analytics.repository.DynamoDBRepository;
import com.planit.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerationScheduler {
    
    private final AnalyticsService analyticsService;
    private final ActionLogRepository actionLogRepository;
    private final DynamoDBRepository dynamoDBRepository;
    private final AIReportPort aiReportPort;
    
    /**
     * 주간 리포트 생성 배치
     * 매주 월요일 새벽 2시에 실행
     * 일 평균 Task 3개 미만인 사용자는 제외
     */
    @Scheduled(cron = "0 0 2 * * MON")
    @SchedulerLock(
        name = "generateWeeklyReports",
        lockAtMostFor = "50m",
        lockAtLeastFor = "10m"
    )
    public void generateWeeklyReports() {
        log.info("주간 리포트 생성 배치 시작");
        
        try {
            YearMonth currentMonth = YearMonth.now();
            log.info("활성 사용자 조회", kv("period", currentMonth.toString()));
            
            // 활성 사용자 목록 조회 (일 평균 Task 3개 이상)
            List<String> activeUsers = getActiveUsers(currentMonth, 3.0);
            log.info("활성 사용자 조회 완료", kv("count", activeUsers.size()));
            
            int successCount = 0;
            int failCount = 0;
            
            for (String userId : activeUsers) {
                try {
                    generateWeeklyReportForUser(userId, currentMonth);
                    successCount++;
                } catch (Exception e) {
                    log.error("주간 리포트 생성 실패", kv("userId", userId), e);
                    failCount++;
                }
            }
            
            log.info("주간 리포트 생성 배치 완료", kv("success", successCount), kv("fail", failCount));
            
        } catch (Exception e) {
            log.error("주간 리포트 생성 배치 실패", e);
        }
    }
    
    /**
     * 월간 리포트 생성 배치
     * 매월 1일 새벽 3시에 실행
     * 일 평균 Task 3개 미만인 사용자는 제외
     */
    @Scheduled(cron = "0 0 3 1 * *")
    @SchedulerLock(
        name = "generateMonthlyReports",
        lockAtMostFor = "50m",
        lockAtLeastFor = "10m"
    )
    public void generateMonthlyReports() {
        log.info("월간 리포트 생성 배치 시작");
        
        try {
            YearMonth previousMonth = YearMonth.now().minusMonths(1);
            
            // 활성 사용자 목록 조회 (일 평균 Task 3개 이상)
            List<String> activeUsers = getActiveUsers(previousMonth, 3.0);
            log.info("활성 사용자 조회 완료", kv("count", activeUsers.size()));
            
            int successCount = 0;
            int failCount = 0;
            
            for (String userId : activeUsers) {
                try {
                    generateMonthlyReportForUser(userId, previousMonth);
                    successCount++;
                } catch (Exception e) {
                    log.error("월간 리포트 생성 실패", kv("userId", userId), e);
                    failCount++;
                }
            }
            
            log.info("월간 리포트 생성 배치 완료", kv("success", successCount), kv("fail", failCount));
            
        } catch (Exception e) {
            log.error("월간 리포트 생성 배치 실패", e);
        }
    }
    
    /**
     * 월간 리포트 생성 배치 (수동 실행용 - 활성 사용자 필터링 없음)
     * BatchController에서 호출
     */
    public void generateMonthlyReportsManual() {
        log.info("월간 리포트 수동 생성 시작 (필터링 없음)");
        
        try {
            YearMonth previousMonth = YearMonth.now().minusMonths(1);
            LocalDateTime start = previousMonth.atDay(1).atStartOfDay();
            LocalDateTime end = previousMonth.atEndOfMonth().atTime(23, 59, 59);
            
            // 모든 사용자 조회 (활성 사용자 필터링 없음)
            List<String> allUsers = actionLogRepository.findDistinctUserIdsByActionTimeBetween(start, end);
            log.info("전체 사용자 조회 완료", kv("count", allUsers.size()));
            
            int successCount = 0;
            int failCount = 0;
            
            for (String userId : allUsers) {
                try {
                    generateMonthlyReportForUser(userId, previousMonth);
                    successCount++;
                } catch (Exception e) {
                    log.error("월간 리포트 생성 실패", kv("userId", userId), e);
                    failCount++;
                }
            }
            
            log.info("월간 리포트 수동 생성 완료", kv("success", successCount), kv("fail", failCount));
            
        } catch (Exception e) {
            log.error("월간 리포트 수동 생성 실패", e);
        }
    }
    
    /**
     * 특정 사용자의 주간 리포트 생성
     */
    private void generateWeeklyReportForUser(String userId, YearMonth targetMonth) {
        log.info("주간 리포트 생성", kv("userId", userId));
        
        // 병렬로 통계 데이터 조회
        CompletableFuture<Map<String, Object>> timelineFuture = 
            analyticsService.calculateTimeline(userId, targetMonth);
        CompletableFuture<Map<String, Object>> patternFuture = 
            analyticsService.analyzePostponePattern(userId, targetMonth);
        
        // 모든 비동기 작업 완료 대기
        CompletableFuture.allOf(timelineFuture, patternFuture).join();
        
        try {
            Map<String, Object> timeline = timelineFuture.get();
            Map<String, Object> pattern = patternFuture.get();
            
            // 타임라인 리포트 생성 및 저장
            if (!timeline.isEmpty()) {
                generateAndSaveReport(userId, targetMonth, "TIMELINE", timeline);
            }
            
            // 패턴 리포트 생성 및 저장
            if (!pattern.isEmpty()) {
                generateAndSaveReport(userId, targetMonth, "PATTERN", pattern);
            }
            
        } catch (Exception e) {
            log.error("주간 리포트 데이터 처리 실패", kv("userId", userId), e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 특정 사용자의 월간 리포트 생성 (public - 수동 실행용)
     */
    public void generateMonthlyReportForUser(String userId, YearMonth targetMonth) {
        log.info("월간 리포트 생성", kv("userId", userId));
        
        // 병렬로 통계 데이터 조회
        CompletableFuture<Map<String, Object>> growthFuture = 
            analyticsService.calculateGrowthRate(userId, targetMonth);
        CompletableFuture<Map<String, Object>> timelineFuture = 
            analyticsService.calculateTimeline(userId, targetMonth);
        CompletableFuture<Map<String, Object>> patternFuture = 
            analyticsService.analyzePostponePattern(userId, targetMonth);
        CompletableFuture<Map<String, Object>> summaryFuture = 
            analyticsService.generateSummary(userId, targetMonth);
        
        // 모든 비동기 작업 완료 대기
        CompletableFuture.allOf(growthFuture, timelineFuture, patternFuture, summaryFuture).join();
        
        try {
            Map<String, Object> growth = growthFuture.get();
            Map<String, Object> timeline = timelineFuture.get();
            Map<String, Object> pattern = patternFuture.get();
            Map<String, Object> summary = summaryFuture.get();
            
            // 각 리포트 타입별로 생성 및 저장
            if (!growth.isEmpty()) {
                generateAndSaveReport(userId, targetMonth, "GROWTH", growth);
            }
            
            if (!timeline.isEmpty()) {
                generateAndSaveReport(userId, targetMonth, "TIMELINE", timeline);
            }
            
            if (!pattern.isEmpty()) {
                generateAndSaveReport(userId, targetMonth, "PATTERN", pattern);
            }
            
            if (!summary.isEmpty()) {
                generateAndSaveReport(userId, targetMonth, "SUMMARY", summary);
            }
            
        } catch (Exception e) {
            log.error("월간 리포트 데이터 처리 실패", kv("userId", userId), e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * AI 리포트 생성 및 DynamoDB 저장
     */
    private void generateAndSaveReport(String userId, YearMonth targetMonth, 
                                      String reportType, Map<String, Object> statisticsData) {
        try {
            // Service B(Python)에 AI 리포트 생성 요청
            AIReportRequest request = AIReportRequest.builder()
                .userId(userId)
                .reportType(reportType)
                .targetPeriod(targetMonth.toString())
                .statisticsData(statisticsData)
                .build();
            
            AIReportResponse response = aiReportPort.generateReport(request);
            
            // AI 리포트 생성 성공 시 DynamoDB에 저장
            if (response.isSuccess() && response.getReportData() != null) {
                dynamoDBRepository.saveReport(
                    userId,
                    targetMonth.toString(),
                    reportType,
                    response.getReportData()
                );
                log.info("리포트 저장 완료", kv("userId", userId), kv("reportType", reportType));
            } else {
                log.warn("AI 리포트 생성 실패", 
                        kv("userId", userId), 
                        kv("reportType", reportType), 
                        kv("error", response.getErrorMessage()));
            }
            
        } catch (Exception e) {
            log.error("리포트 생성 및 저장 실패", 
                    kv("userId", userId), 
                    kv("reportType", reportType), e);
        }
    }
    
    /**
     * 활성 사용자 목록 조회
     * 일 평균 Task 수가 minAvgTasks 이상인 사용자만 반환
     */
    private List<String> getActiveUsers(YearMonth targetMonth, double minAvgTasks) {
        LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime end = targetMonth.atEndOfMonth().atTime(23, 59, 59);
        long days = targetMonth.lengthOfMonth();
        
        // ActionLog에서 해당 기간에 활동한 모든 유니크한 userId 추출
        List<String> allUsers = actionLogRepository.findDistinctUserIdsByActionTimeBetween(start, end);
        log.debug("전체 사용자 조회", kv("count", allUsers.size()), kv("period", targetMonth.toString()));
        
        List<String> activeUsers = new ArrayList<>();
        for (String userId : allUsers) {
            Double avgTaskCount = actionLogRepository.calculateAverageDailyTaskCount(
                userId, start, end, days
            );
            
            if (avgTaskCount != null && avgTaskCount >= minAvgTasks) {
                activeUsers.add(userId);
                log.debug("활성 사용자", kv("userId", userId), kv("avgDailyTasks", avgTaskCount));
            }
        }
        
        return activeUsers;
    }
}
