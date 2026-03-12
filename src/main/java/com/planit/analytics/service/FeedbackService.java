/**
 * Feedback Service
 * DynamoDB에서 AI 리포트를 조회하여 사용자에게 피드백을 제공하는 서비스
 * @since 2026-03-03
 */
package com.planit.analytics.service;

import com.planit.analytics.dto.DayOfWeekStats;
import com.planit.analytics.repository.ActionLogRepository;
import com.planit.analytics.repository.DynamoDBRepository;
import com.planit.global.CustomException;
import com.planit.global.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {
    
    private final DynamoDBRepository dynamoDBRepository;
    private final ActionLogRepository actionLogRepository;
    
    /**
     * 일간 응원 피드백 조회
     * 오늘 요일의 평균 대비 수행률 차이를 기반으로 응원 메시지 제공
     */
    public Map<String, Object> getDailyCheer(String userId) {
        log.info("Getting daily cheer for user: {}", userId);
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        
        // 최근 3개월 데이터로 요일별 통계 계산
        LocalDateTime threeMonthsAgo = today.minusMonths(3).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        
        List<DayOfWeekStats> stats = actionLogRepository.calculateDayOfWeekStats(
            userId, threeMonthsAgo, now
        );
        
        // 오늘 요일의 통계 찾기
        Optional<DayOfWeekStats> todayStats = stats.stream()
            .filter(s -> s.getDayOfWeek().equals(dayOfWeek.name()))
            .findFirst();
        
        // 전체 평균 완료율 계산
        double avgRate = stats.stream()
            .mapToDouble(DayOfWeekStats::getCompletionRate)
            .average()
            .orElse(0.0);
        
        Map<String, Object> cheerData = new HashMap<>();
        
        if (todayStats.isPresent()) {
            double todayRate = todayStats.get().getCompletionRate();
            double diff = todayRate - avgRate;
            boolean isHigher = diff >= 0;
            
            cheerData.put("diffFromAvg", String.format("%+.0f%%", diff));
            cheerData.put("isHigherThanAvg", isHigher);
            cheerData.put("message", generateCheerMessage(dayOfWeek, diff, isHigher));
        } else {
            cheerData.put("diffFromAvg", "0%");
            cheerData.put("isHigherThanAvg", true);
            cheerData.put("message", getDefaultCheerMessage(dayOfWeek));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("targetDate", today.toString());
        result.put("dayOfWeek", dayOfWeek.name());
        result.put("cheerData", cheerData);
        
        return result;
    }
    
    /**
     * AI 피드백 대시보드 조회
     * 성장 격려, 타임라인, 미룸 패턴, 종합 피드백을 한 번에 조회
     * 당월 데이터가 없으면 전월 데이터를 조회
     */
    public Map<String, Object> getDashboard(String userId, String yearMonth, Integer week) {
        log.info("Getting dashboard for user: {}, yearMonth: {}, week: {}", userId, yearMonth, week);
        
        if (userId == null || userId.trim().isEmpty() || yearMonth == null || week == null) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        YearMonth requestedMonth;
        try {
            requestedMonth = YearMonth.parse(yearMonth);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        // 당월 데이터 조회 시도
        Map<String, Object> growth = getReportWithFallback(userId, requestedMonth, "GROWTH");
        Map<String, Object> timeline = getReportWithFallback(userId, requestedMonth, "TIMELINE");
        Map<String, Object> pattern = getReportWithFallback(userId, requestedMonth, "PATTERN");
        Map<String, Object> summary = getReportWithFallback(userId, requestedMonth, "SUMMARY");
        
        // 실제 조회된 월 확인 (모든 리포트가 null이면 당월, 하나라도 있으면 해당 월)
        String actualMonth = yearMonth;
        if (growth == null && timeline == null && pattern == null && summary == null) {
            // 모두 없으면 전월 데이터 확인
            YearMonth previousMonth = requestedMonth.minusMonths(1);
            log.info("No data found for {}, trying previous month: {}", yearMonth, previousMonth);
            
            growth = getReportWithFallback(userId, previousMonth, "GROWTH");
            timeline = getReportWithFallback(userId, previousMonth, "TIMELINE");
            pattern = getReportWithFallback(userId, previousMonth, "PATTERN");
            summary = getReportWithFallback(userId, previousMonth, "SUMMARY");
            
            actualMonth = previousMonth.toString();
        }
        
        Map<String, Object> feedbacks = new HashMap<>();
        feedbacks.put("growth", growth != null ? growth : getDefaultGrowthFeedback());
        feedbacks.put("timeline", timeline != null ? timeline : getDefaultTimelineFeedback());
        feedbacks.put("pattern", pattern != null ? pattern : getDefaultPatternFeedback());
        feedbacks.put("summary", summary != null ? summary : getDefaultSummaryFeedback());
        
        Map<String, Object> targetPeriod = new HashMap<>();
        targetPeriod.put("month", actualMonth);
        targetPeriod.put("week", week);
        
        Map<String, Object> result = new HashMap<>();
        result.put("targetPeriod", targetPeriod);
        result.put("feedbacks", feedbacks);
        
        return result;
    }
    
    /**
     * 리포트 조회 (예외 발생 시 null 반환)
     */
    private Map<String, Object> getReportWithFallback(String userId, YearMonth yearMonth, String reportType) {
        try {
            return dynamoDBRepository.getReport(userId, yearMonth.toString(), reportType);
        } catch (Exception e) {
            log.debug("Report not found: userId={}, yearMonth={}, type={}", userId, yearMonth, reportType);
            return null;
        }
    }
    
    private String generateCheerMessage(DayOfWeek dayOfWeek, double diff, boolean isHigher) {
        String dayName = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN);
        if (isHigher) {
            return diff >= 10 ? String.format("%s은 평소보다 수행률이 %.0f%% 높아요! 화이팅!", dayName, diff) 
                             : String.format("%s도 좋은 하루가 될 거예요!", dayName);
        }
        return String.format("%s도 나만의 페이스로 천천히 진행해봐요!", dayName);
    }
    
    private String getDefaultCheerMessage(DayOfWeek dayOfWeek) {
        return String.format("좋은 %s 되세요! 오늘도 할 수 있어요!", dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN));
    }
    
    private Map<String, Object> getDefaultGrowthFeedback() {
        Map<String, Object> f = new HashMap<>();
        f.put("topicName", "전체"); f.put("growthRate", 0); f.put("message", "데이터 수집 중이에요.");
        return f;
    }
    
    private Map<String, Object> getDefaultTimelineFeedback() {
        Map<String, Object> f = new HashMap<>();
        f.put("chartData", new ArrayList<>()); f.put("message", "최근 활동을 분석 중이에요.");
        return f;
    }
    
    private Map<String, Object> getDefaultPatternFeedback() {
        Map<String, Object> f = new HashMap<>();
        f.put("worstDay", "SUNDAY"); f.put("avgPostponeCount", 0); f.put("message", "패턴 분석 중이에요.");
        f.put("chart", new ArrayList<>());
        return f;
    }
    
    private Map<String, Object> getDefaultSummaryFeedback() {
        Map<String, Object> f = new HashMap<>();
        f.put("achievementTrend", "0%"); f.put("bestFocusTime", "08:00-10:00"); f.put("message", "데이터가 쌓이면 분석을 제공할게요!");
        return f;
    }
}
