/**
 * Analytics Service
 * 사용자의 할 일 처리 데이터를 분석하는 서비스
 * 비동기 처리를 통해 병렬로 통계를 계산
 * @since 2026-03-03
 */
package com.planit.analytics.service;

import com.planit.analytics.dto.DayOfWeekStats;
import com.planit.analytics.entity.ActionLogEntity;
import com.planit.analytics.entity.ActionType;
import com.planit.analytics.repository.ActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    
    private final ActionLogRepository actionLogRepository;
    
    /**
     * 성장률 계산 (비동기)
     * 이전 3개월 대비 현재 월의 완료율 증가율 계산
     * 
     * @param userId 사용자 ID
     * @param targetMonth 대상 월 (예: 2026-02)
     * @return 성장률 데이터 (topicName, growthRate, previousRate, currentRate)
     */
    @Async
    public CompletableFuture<Map<String, Object>> calculateGrowthRate(String userId, YearMonth targetMonth) {
        log.info("Calculating growth rate for user: {}, month: {}", userId, targetMonth);
        
        try {
            // 현재 월 데이터 조회
            LocalDateTime currentStart = targetMonth.atDay(1).atStartOfDay();
            LocalDateTime currentEnd = targetMonth.atEndOfMonth().atTime(23, 59, 59);
            List<ActionLogEntity> currentLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
                userId, currentStart, currentEnd
            );
            
            // 이전 3개월 데이터 조회
            YearMonth threeMonthsAgo = targetMonth.minusMonths(3);
            LocalDateTime previousStart = threeMonthsAgo.atDay(1).atStartOfDay();
            LocalDateTime previousEnd = targetMonth.minusMonths(1).atEndOfMonth().atTime(23, 59, 59);
            List<ActionLogEntity> previousLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
                userId, previousStart, previousEnd
            );
            
            // 완료율 계산
            double currentRate = calculateCompletionRate(currentLogs);
            double previousRate = calculateCompletionRate(previousLogs);
            
            // 성장률 계산
            double growthRate = 0.0;
            if (previousRate > 0) {
                growthRate = ((currentRate - previousRate) / previousRate) * 100;
            }
            
            // 가장 성장한 주제 찾기
            String topTopic = findTopGrowthTopic(userId, currentStart, currentEnd, previousStart, previousEnd);
            
            Map<String, Object> result = new HashMap<>();
            result.put("topicName", topTopic != null ? topTopic : "전체");
            result.put("growthRate", Math.round(growthRate));
            result.put("previousRate", Math.round(previousRate));
            result.put("currentRate", Math.round(currentRate));
            
            log.info("Growth rate calculated: {}", result);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Failed to calculate growth rate", e);
            return CompletableFuture.completedFuture(new HashMap<>());
        }
    }
    
    /**
     * 타임라인 분석 (비동기)
     * 최근 6개월간의 월별 완료율 추이 계산
     * 
     * @param userId 사용자 ID
     * @param targetMonth 대상 월
     * @return 월별 완료율 차트 데이터
     */
    @Async
    public CompletableFuture<Map<String, Object>> calculateTimeline(String userId, YearMonth targetMonth) {
        log.info("Calculating timeline for user: {}, month: {}", userId, targetMonth);
        
        try {
            List<Map<String, Object>> chartData = new ArrayList<>();
            
            // 최근 6개월 데이터 수집
            for (int i = 5; i >= 0; i--) {
                YearMonth month = targetMonth.minusMonths(i);
                LocalDateTime start = month.atDay(1).atStartOfDay();
                LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59);
                
                List<ActionLogEntity> logs = actionLogRepository.findByUserIdAndActionTimeBetween(
                    userId, start, end
                );
                
                double rate = calculateCompletionRate(logs);
                
                Map<String, Object> monthData = new HashMap<>();
                monthData.put("month", month.getMonth().getValue() + "월");
                monthData.put("rate", Math.round(rate));
                chartData.add(monthData);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("chartData", chartData);
            
            log.info("Timeline calculated with {} months", chartData.size());
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Failed to calculate timeline", e);
            return CompletableFuture.completedFuture(new HashMap<>());
        }
    }
    
    /**
     * 미룸 패턴 분석 (비동기)
     * 요일별 미룸 횟수 및 가장 미루는 요일 식별
     * 
     * @param userId 사용자 ID
     * @param targetMonth 대상 월
     * @return 미룸 패턴 데이터 (worstDay, avgPostponeCount, chart)
     */
    @Async
    public CompletableFuture<Map<String, Object>> analyzePostponePattern(String userId, YearMonth targetMonth) {
        log.info("Analyzing postpone pattern for user: {}, month: {}", userId, targetMonth);
        
        try {
            // 최근 3개월 데이터 조회
            YearMonth threeMonthsAgo = targetMonth.minusMonths(3);
            LocalDateTime start = threeMonthsAgo.atDay(1).atStartOfDay();
            LocalDateTime end = targetMonth.atEndOfMonth().atTime(23, 59, 59);
            
            List<ActionLogEntity> postponeLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
                userId, start, end
            ).stream()
            .filter(log -> log.getActionType() == ActionType.POSTPONED)
            .collect(Collectors.toList());
            
            // 요일별 미룸 횟수 집계
            Map<String, Long> postponeByDay = postponeLogs.stream()
                .collect(Collectors.groupingBy(
                    ActionLogEntity::getDayOfWeek,
                    Collectors.counting()
                ));
            
            // 가장 미루는 요일 찾기
            String worstDay = postponeByDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("SUNDAY");
            
            // 평균 미룸 횟수 계산
            double avgPostponeCount = postponeLogs.size() / 3.0; // 3개월 평균
            
            // 차트 데이터 생성
            List<Map<String, Object>> chartData = new ArrayList<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                Map<String, Object> dayData = new HashMap<>();
                dayData.put("day", day.name());
                dayData.put("count", postponeByDay.getOrDefault(day.name(), 0L));
                chartData.add(dayData);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("worstDay", worstDay);
            result.put("avgPostponeCount", Math.round(avgPostponeCount));
            result.put("chart", chartData);
            
            log.info("Postpone pattern analyzed: worst day = {}", worstDay);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Failed to analyze postpone pattern", e);
            return CompletableFuture.completedFuture(new HashMap<>());
        }
    }
    
    /**
     * 종합 피드백 생성 (비동기)
     * 달성률 추이, 최적 집중 시간대 등 종합 분석
     * 
     * @param userId 사용자 ID
     * @param targetMonth 대상 월
     * @return 종합 피드백 데이터
     */
    @Async
    public CompletableFuture<Map<String, Object>> generateSummary(String userId, YearMonth targetMonth) {
        log.info("Generating summary for user: {}, month: {}", userId, targetMonth);
        
        try {
            // 현재 월과 이전 월 데이터 조회
            LocalDateTime currentStart = targetMonth.atDay(1).atStartOfDay();
            LocalDateTime currentEnd = targetMonth.atEndOfMonth().atTime(23, 59, 59);
            List<ActionLogEntity> currentLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
                userId, currentStart, currentEnd
            );
            
            YearMonth previousMonth = targetMonth.minusMonths(1);
            LocalDateTime previousStart = previousMonth.atDay(1).atStartOfDay();
            LocalDateTime previousEnd = previousMonth.atEndOfMonth().atTime(23, 59, 59);
            List<ActionLogEntity> previousLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
                userId, previousStart, previousEnd
            );
            
            // 달성률 추이 계산
            double currentRate = calculateCompletionRate(currentLogs);
            double previousRate = calculateCompletionRate(previousLogs);
            double trend = currentRate - previousRate;
            String trendStr = (trend >= 0 ? "+" : "") + Math.round(trend) + "%";
            
            // 최적 집중 시간대 찾기
            String bestFocusTime = findBestFocusTime(currentLogs);
            
            Map<String, Object> result = new HashMap<>();
            result.put("achievementTrend", trendStr);
            result.put("bestFocusTime", bestFocusTime);
            result.put("currentRate", Math.round(currentRate));
            
            log.info("Summary generated: trend = {}, focus time = {}", trendStr, bestFocusTime);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Failed to generate summary", e);
            return CompletableFuture.completedFuture(new HashMap<>());
        }
    }
    
    // === Private Helper Methods ===
    
    /**
     * 완료율 계산
     */
    private double calculateCompletionRate(List<ActionLogEntity> logs) {
        if (logs.isEmpty()) {
            return 0.0;
        }
        
        long completedCount = logs.stream()
            .filter(log -> log.getActionType() == ActionType.COMPLETED)
            .count();
        
        return (completedCount * 100.0) / logs.size();
    }
    
    /**
     * 가장 성장한 주제 찾기
     */
    private String findTopGrowthTopic(String userId, LocalDateTime currentStart, LocalDateTime currentEnd,
                                      LocalDateTime previousStart, LocalDateTime previousEnd) {
        // 주제별 현재 완료율
        Map<Long, Double> currentRates = new HashMap<>();
        List<ActionLogEntity> currentLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
            userId, currentStart, currentEnd
        );
        
        currentLogs.stream()
            .filter(log -> log.getGoalsId() != null)
            .collect(Collectors.groupingBy(ActionLogEntity::getGoalsId))
            .forEach((goalsId, logs) -> {
                currentRates.put(goalsId, calculateCompletionRate(logs));
            });
        
        // 주제별 이전 완료율
        Map<Long, Double> previousRates = new HashMap<>();
        List<ActionLogEntity> previousLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
            userId, previousStart, previousEnd
        );
        
        previousLogs.stream()
            .filter(log -> log.getGoalsId() != null)
            .collect(Collectors.groupingBy(ActionLogEntity::getGoalsId))
            .forEach((goalsId, logs) -> {
                previousRates.put(goalsId, calculateCompletionRate(logs));
            });
        
        // 가장 성장한 주제 찾기
        Long topGoalsId = currentRates.entrySet().stream()
            .filter(entry -> previousRates.containsKey(entry.getKey()))
            .max(Comparator.comparingDouble(entry -> 
                entry.getValue() - previousRates.get(entry.getKey())
            ))
            .map(Map.Entry::getKey)
            .orElse(null);
        
        // 실제로는 goals_id로 주제명을 조회해야 하지만, 여기서는 ID를 반환
        return topGoalsId != null ? "주제#" + topGoalsId : null;
    }
    
    /**
     * 최적 집중 시간대 찾기
     * 완료율이 가장 높은 시간대 반환
     */
    private String findBestFocusTime(List<ActionLogEntity> logs) {
        if (logs.isEmpty()) {
            return "08:00-10:00"; // 기본값
        }
        
        // 시간대별 완료율 계산
        Map<Integer, List<ActionLogEntity>> logsByHour = logs.stream()
            .collect(Collectors.groupingBy(ActionLogEntity::getHourOfDay));
        
        Integer bestHour = logsByHour.entrySet().stream()
            .max(Comparator.comparingDouble(entry -> 
                calculateCompletionRate(entry.getValue())
            ))
            .map(Map.Entry::getKey)
            .orElse(8);
        
        return String.format("%02d:00-%02d:00", bestHour, bestHour + 2);
    }
}
