/**
 * Analytics Service
 * 사용자의 할 일 처리 데이터를 분석하는 서비스
 * 비동기 처리를 통해 병렬로 통계를 계산
 * @since 2026-03-03
 */
package com.planit.analytics.service;

import com.planit.analytics.entity.ActionLogEntity;
import com.planit.analytics.entity.ActionType;
import com.planit.analytics.repository.ActionLogRepository;
import com.planit.analytics.repository.GoalsRepository;
import com.planit.analytics.repository.CategoryListRepository;
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
    private final GoalsRepository goalsRepository;
    private final CategoryListRepository categoryListRepository;
    
    /**
     * 성장률 계산 (비동기)
     * 이전 3개월 대비 현재 월의 완료율 증가율 계산
     * 실제 DB 데이터를 기반으로 동적 계산
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
            
            log.info("Current month logs count: {}", currentLogs.size());
            
            // 이전 3개월 데이터 조회 (현재 월 제외)
            YearMonth threeMonthsAgo = targetMonth.minusMonths(3);
            LocalDateTime previousStart = threeMonthsAgo.atDay(1).atStartOfDay();
            LocalDateTime previousEnd = targetMonth.minusMonths(1).atEndOfMonth().atTime(23, 59, 59);
            List<ActionLogEntity> previousLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
                userId, previousStart, previousEnd
            );
            
            log.info("Previous 3 months logs count: {}", previousLogs.size());
            
            // 데이터가 없으면 빈 결과 반환
            if (currentLogs.isEmpty() && previousLogs.isEmpty()) {
                log.warn("No action logs found for user: {}", userId);
                return CompletableFuture.completedFuture(new HashMap<>());
            }
            
            // 완료율 계산 (실제 COMPLETED 건수 기반)
            double currentRate = calculateCompletionRate(currentLogs);
            double previousRate = calculateCompletionRate(previousLogs);
            
            log.info("Current completion rate: {}%, Previous completion rate: {}%", 
                Math.round(currentRate), Math.round(previousRate));
            
            // 성장률 계산 (이전 대비 증감률)
            double growthRate = 0.0;
            if (previousRate > 0) {
                growthRate = ((currentRate - previousRate) / previousRate) * 100;
            } else if (currentRate > 0) {
                // 이전 데이터가 없고 현재만 있으면 100% 성장으로 간주
                growthRate = 100.0;
            }
            
            log.info("Calculated growth rate: {}%", Math.round(growthRate));
            
            // 가장 성장한 주제 찾기 (실제 DB 데이터 기반)
            String topTopic = findTopGrowthTopic(userId, currentStart, currentEnd, previousStart, previousEnd);
            
            log.info("Top growth topic: {}", topTopic);
            
            Map<String, Object> result = new HashMap<>();
            result.put("topicName", topTopic != null ? topTopic : "전체");
            result.put("growthRate", Math.round(growthRate));
            result.put("previousRate", Math.round(previousRate));
            result.put("currentRate", Math.round(currentRate));
            
            log.info("Growth rate calculation completed: {}", result);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Failed to calculate growth rate for user: {}", userId, e);
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
            
            // 총 할 일 및 완료한 할 일 계산
            int totalTasks = currentLogs.size();
            long completedTasks = currentLogs.stream()
                .filter(log -> log.getActionType() == ActionType.COMPLETED)
                .count();
            
            Map<String, Object> result = new HashMap<>();
            result.put("achievementTrend", trendStr);
            result.put("bestFocusTime", bestFocusTime);
            result.put("currentRate", Math.round(currentRate));
            result.put("totalTasks", totalTasks);
            result.put("completedTasks", (int) completedTasks);
            result.put("completionRate", Math.round(currentRate));  // Python에서 사용할 수 있도록 추가
            
            log.info("Summary generated: trend = {}, focus time = {}, currentRate = {}%, totalTasks = {}, completedTasks = {}", 
                trendStr, bestFocusTime, Math.round(currentRate), totalTasks, completedTasks);
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
     * goals_id NULL(미분류) 처리 포함
     * 실제 완료율 증가량 기반으로 계산
     */
    private String findTopGrowthTopic(String userId, LocalDateTime currentStart, LocalDateTime currentEnd,
                                      LocalDateTime previousStart, LocalDateTime previousEnd) {
        log.info("Finding top growth topic for user: {}", userId);
        
        try {
            // 현재 월 데이터 조회
            List<ActionLogEntity> currentLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
                userId, currentStart, currentEnd
            );
            
            // 이전 3개월 데이터 조회
            List<ActionLogEntity> previousLogs = actionLogRepository.findByUserIdAndActionTimeBetween(
                userId, previousStart, previousEnd
            );
            
            // 주제별 현재 완료율 계산 (goals_id 기준)
            Map<Long, Double> currentRates = new HashMap<>();
            Map<Long, Integer> currentCounts = new HashMap<>();
            
            // goals_id가 있는 로그 그룹화
            currentLogs.stream()
                .filter(log -> log.getGoalsId() != null)
                .collect(Collectors.groupingBy(ActionLogEntity::getGoalsId))
                .forEach((goalsId, logs) -> {
                    currentRates.put(goalsId, calculateCompletionRate(logs));
                    currentCounts.put(goalsId, logs.size());
                });
            
            // goals_id가 NULL인 로그 (미분류)
            List<ActionLogEntity> currentNullGoalsLogs = currentLogs.stream()
                .filter(log -> log.getGoalsId() == null)
                .collect(Collectors.toList());
            
            if (!currentNullGoalsLogs.isEmpty()) {
                currentRates.put(0L, calculateCompletionRate(currentNullGoalsLogs));
                currentCounts.put(0L, currentNullGoalsLogs.size());
            }
            
            log.info("Current period - Total categories: {}, Null goals count: {}", 
                currentRates.size(), currentNullGoalsLogs.size());
            
            // 주제별 이전 완료율 계산
            Map<Long, Double> previousRates = new HashMap<>();
            Map<Long, Integer> previousCounts = new HashMap<>();
            
            previousLogs.stream()
                .filter(log -> log.getGoalsId() != null)
                .collect(Collectors.groupingBy(ActionLogEntity::getGoalsId))
                .forEach((goalsId, logs) -> {
                    previousRates.put(goalsId, calculateCompletionRate(logs));
                    previousCounts.put(goalsId, logs.size());
                });
            
            // goals_id가 NULL인 로그 (미분류)
            List<ActionLogEntity> previousNullGoalsLogs = previousLogs.stream()
                .filter(log -> log.getGoalsId() == null)
                .collect(Collectors.toList());
            
            if (!previousNullGoalsLogs.isEmpty()) {
                previousRates.put(0L, calculateCompletionRate(previousNullGoalsLogs));
                previousCounts.put(0L, previousNullGoalsLogs.size());
            }
            
            log.info("Previous period - Total categories: {}, Null goals count: {}", 
                previousRates.size(), previousNullGoalsLogs.size());
            
            // 가장 성장한 주제 찾기 (완료율 증가량 기준)
            Long topGoalsId = currentRates.entrySet().stream()
                .filter(entry -> {
                    // 현재와 이전 모두 데이터가 있는 주제만 비교
                    Long goalsId = entry.getKey();
                    return previousRates.containsKey(goalsId) && 
                           currentCounts.getOrDefault(goalsId, 0) >= 3; // 최소 3개 이상의 로그
                })
                .max(Comparator.comparingDouble(entry -> {
                    Long goalsId = entry.getKey();
                    double currentRate = entry.getValue();
                    double previousRate = previousRates.get(goalsId);
                    double growth = currentRate - previousRate;
                    log.debug("GoalsId: {}, Current: {}%, Previous: {}%, Growth: {}%", 
                        goalsId, Math.round(currentRate), Math.round(previousRate), Math.round(growth));
                    return growth;
                }))
                .map(Map.Entry::getKey)
                .orElse(null);
            
            // 주제명 조회
            if (topGoalsId != null) {
                if (topGoalsId == 0L) {
                    // goals_id가 NULL인 경우 (미분류)
                    log.info("Top growth topic: 미분류 (goals_id = NULL)");
                    return "미분류";
                } else {
                    // goals_id로 실제 카테고리명 조회
                    try {
                        String categoryName = goalsRepository.findByGoalsId(topGoalsId)
                            .flatMap(goals -> categoryListRepository.findByListId(goals.getListId()))
                            .map(categoryList -> categoryList.getName())
                            .orElse("전체");
                        
                        log.info("Top growth topic: {} (goals_id = {})", categoryName, topGoalsId);
                        return categoryName;
                    } catch (Exception e) {
                        log.warn("Failed to fetch category name for goalsId: {}", topGoalsId, e);
                        return "전체";
                    }
                }
            }
            
            // 성장한 주제가 없으면 가장 많이 완료한 주제 반환
            Long mostActiveGoalsId = currentCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
            
            if (mostActiveGoalsId != null) {
                if (mostActiveGoalsId == 0L) {
                    log.info("Most active topic: 미분류 (goals_id = NULL)");
                    return "미분류";
                } else {
                    try {
                        String categoryName = goalsRepository.findByGoalsId(mostActiveGoalsId)
                            .flatMap(goals -> categoryListRepository.findByListId(goals.getListId()))
                            .map(categoryList -> categoryList.getName())
                            .orElse("전체");
                        
                        log.info("Most active topic: {} (goals_id = {})", categoryName, mostActiveGoalsId);
                        return categoryName;
                    } catch (Exception e) {
                        log.warn("Failed to fetch category name for goalsId: {}", mostActiveGoalsId, e);
                        return "전체";
                    }
                }
            }
            
            log.info("No specific topic found, returning '전체'");
            return "전체";
            
        } catch (Exception e) {
            log.error("Failed to find top growth topic", e);
            return "전체";
        }
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
