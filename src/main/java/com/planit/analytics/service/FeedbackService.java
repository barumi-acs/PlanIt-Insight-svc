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
     * 
     * @param userId 사용자 ID
     * @return 일간 응원 피드백 데이터
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
            // 데이터가 없을 경우 기본 메시지
            cheerData.put("diffFromAvg", "0%");
            cheerData.put("isHigherThanAvg", true);
            cheerData.put("message", getDefaultCheerMessage(dayOfWeek));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("targetDate", today.toString());
        result.put("dayOfWeek", dayOfWeek.name());
        result.put("cheerData", cheerData);
        
        log.info("Daily cheer generated for user: {}", userId);
        return result;
    }
    
    /**
     * AI 피드백 대시보드 조회
     * 성장 격려, 타임라인, 미룸 패턴, 종합 피드백을 한 번에 조회
     * 
     * @param userId 사용자 ID
     * @param yearMonth 대상 월 (예: "2026-02")
     * @param week 대상 주차
     * @return 대시보드 피드백 데이터
     */
    public Map<String, Object> getDashboard(String userId, String yearMonth, Integer week) {
        log.info("Getting dashboard for user: {}, yearMonth: {}, week: {}", userId, yearMonth, week);
        
        // 파라미터 검증
        if (userId == null || userId.trim().isEmpty()) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (yearMonth == null || yearMonth.trim().isEmpty()) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        if (week == null) {
            throw new CustomException(ErrorCode.C4001);
        }
        
        // YearMonth 파싱 검증
        try {
            YearMonth.parse(yearMonth);
        } catch (Exception e) {
            log.error("Invalid yearMonth format: {}", yearMonth);
            throw new CustomException(ErrorCode.C4001);
        }
        
        // DynamoDB에서 각 리포트 타입별로 조회
        Map<String, Object> growth = dynamoDBRepository.getReport(userId, yearMonth, "GROWTH");
        Map<String, Object> timeline = dynamoDBRepository.getReport(userId, yearMonth, "TIMELINE");
        Map<String, Object> pattern = dynamoDBRepository.getReport(userId, yearMonth, "PATTERN");
        Map<String, Object> summary = dynamoDBRepository.getReport(userId, yearMonth, "SUMMARY");
        
        // 모든 리포트가 없으면 에러
        if (growth == null && timeline == null && pattern == null && summary == null) {
            log.warn("No report data found for user: {}, yearMonth: {}", userId, yearMonth);
            throw new CustomException(ErrorCode.IS4041);
        }
        
        // 피드백 데이터 구성
        Map<String, Object> feedbacks = new HashMap<>();
        feedbacks.put("growth", growth != null ? growth : getDefaultGrowthFeedback());
        feedbacks.put("timeline", timeline != null ? timeline : getDefaultTimelineFeedback());
        feedbacks.put("pattern", pattern != null ? pattern : getDefaultPatternFeedback());
        feedbacks.put("summary", summary != null ? summary : getDefaultSummaryFeedback());
        
        // 대시보드 응답 구성
        Map<String, Object> targetPeriod = new HashMap<>();
        targetPeriod.put("month", yearMonth);
        targetPeriod.put("week", week);
        
        Map<String, Object> result = new HashMap<>();
        result.put("targetPeriod", targetPeriod);
        result.put("feedbacks", feedbacks);
        
        log.info("Dashboard generated for user: {}", userId);
        return result;
    }
    
    // === Private Helper Methods ===
    
    /**
     * 요일별 응원 메시지 생성
     */
    private String generateCheerMessage(DayOfWeek dayOfWeek, double diff, boolean isHigher) {
        String dayName = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN);
        
        if (isHigher) {
            if (diff >= 10) {
                return String.format("%s은 평소보다 수행률이 %.0f%% 높아요! 이 기세를 몰아 오늘 계획도 완수해볼까요?", 
                    dayName, Math.abs(diff));
            } else {
                return String.format("%s도 좋은 하루가 될 거예요! 오늘도 화이팅!", dayName);
            }
        } else {
            if (Math.abs(diff) >= 10) {
                return String.format("%s은 평소보다 조금 힘든 날이지만, 작은 목표부터 시작해보세요!", dayName);
            } else {
                return String.format("%s도 나만의 페이스로 천천히 진행해봐요!", dayName);
            }
        }
    }
    
    /**
     * 기본 응원 메시지 (데이터 없을 때)
     */
    private String getDefaultCheerMessage(DayOfWeek dayOfWeek) {
        String dayName = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN);
        return String.format("좋은 %s 되세요! 오늘도 할 수 있어요!", dayName);
    }
    
    /**
     * 기본 성장 피드백 (데이터 없을 때)
     */
    private Map<String, Object> getDefaultGrowthFeedback() {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("topicName", "전체");
        feedback.put("growthRate", 0);
        feedback.put("message", "아직 분석할 데이터가 부족해요. 꾸준히 기록해보세요!");
        return feedback;
    }
    
    /**
     * 기본 타임라인 피드백 (데이터 없을 때)
     */
    private Map<String, Object> getDefaultTimelineFeedback() {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("chartData", new ArrayList<>());
        feedback.put("message", "최근 활동 데이터를 수집 중이에요.");
        return feedback;
    }
    
    /**
     * 기본 패턴 피드백 (데이터 없을 때)
     */
    private Map<String, Object> getDefaultPatternFeedback() {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("worstDay", "SUNDAY");
        feedback.put("avgPostponeCount", 0);
        feedback.put("message", "아직 패턴을 분석하기에 데이터가 부족해요.");
        feedback.put("chart", new ArrayList<>());
        return feedback;
    }
    
    /**
     * 기본 종합 피드백 (데이터 없을 때)
     */
    private Map<String, Object> getDefaultSummaryFeedback() {
        Map<String, Object> feedback = new HashMap<>();
        feedback.put("achievementTrend", "0%");
        feedback.put("bestFocusTime", "08:00-10:00");
        feedback.put("message", "데이터가 쌓이면 더 정확한 분석을 제공할게요!");
        return feedback;
    }
}
