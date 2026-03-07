# Growth 데이터 계산 로직 완전 개선

## 작업 완료 일시
2026-03-08

## 문제점
- `topicName`이 "전체"로 고정
- `growthRate`가 더미 값 `-24`로 고정
- 실제 DB 데이터를 사용하지 않고 하드코딩된 값 반환

## 해결 방법

### 1. 실제 DB 쿼리 기반 동적 계산 ✅

#### calculateGrowthRate 메서드 개선
**파일**: `PlanIt-Insight-svc/src/main/java/com/planit/analytics/service/AnalyticsService.java`

```java
@Async
public CompletableFuture<Map<String, Object>> calculateGrowthRate(String userId, YearMonth targetMonth) {
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
}
```

#### 주요 개선 사항
1. **실제 DB 조회**: `actionLogRepository.findByUserIdAndActionTimeBetween()` 사용
2. **동적 계산**: 하드코딩 제거, 실제 COMPLETED 건수 기반 완료율 계산
3. **상세한 로깅**: 각 단계마다 로그 출력으로 디버깅 용이
4. **빈 데이터 처리**: 데이터가 없으면 빈 HashMap 반환

### 2. goals_id NULL(미분류) 처리 명확화 ✅

#### findTopGrowthTopic 메서드 완전 개편
**파일**: `PlanIt-Insight-svc/src/main/java/com/planit/analytics/service/AnalyticsService.java`

```java
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
        
        // goals_id가 NULL인 로그 (미분류) - 0L로 매핑
        List<ActionLogEntity> currentNullGoalsLogs = currentLogs.stream()
            .filter(log -> log.getGoalsId() == null)
            .collect(Collectors.toList());
        
        if (!currentNullGoalsLogs.isEmpty()) {
            currentRates.put(0L, calculateCompletionRate(currentNullGoalsLogs));
            currentCounts.put(0L, currentNullGoalsLogs.size());
        }
        
        log.info("Current period - Total categories: {}, Null goals count: {}", 
            currentRates.size(), currentNullGoalsLogs.size());
        
        // 주제별 이전 완료율 계산 (동일한 방식)
        Map<Long, Double> previousRates = new HashMap<>();
        Map<Long, Integer> previousCounts = new HashMap<>();
        
        previousLogs.stream()
            .filter(log -> log.getGoalsId() != null)
            .collect(Collectors.groupingBy(ActionLogEntity::getGoalsId))
            .forEach((goalsId, logs) -> {
                previousRates.put(goalsId, calculateCompletionRate(logs));
                previousCounts.put(goalsId, logs.size());
            });
        
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
```

#### 주요 개선 사항
1. **goals_id NULL 처리**: 0L로 매핑하여 "미분류"로 명시
2. **실제 성장률 계산**: 완료율 증가량 기반으로 가장 성장한 주제 찾기
3. **최소 데이터 요구**: 최소 3개 이상의 로그가 있는 주제만 비교
4. **Fallback 로직**: 성장한 주제가 없으면 가장 활발한 주제 반환
5. **DB 연동**: `goalsRepository`와 `categoryListRepository`로 실제 카테고리명 조회
6. **상세한 로깅**: 각 단계마다 디버그 로그 출력

## 데이터 흐름

### 1. Growth Rate 계산
```
User ID + Target Month
    ↓
현재 월 ActionLog 조회 (COMPLETED 건수)
    ↓
이전 3개월 ActionLog 조회 (COMPLETED 건수)
    ↓
완료율 계산 (COMPLETED / TOTAL * 100)
    ↓
성장률 계산 ((현재 - 이전) / 이전 * 100)
    ↓
Result: {growthRate, currentRate, previousRate}
```

### 2. Top Growth Topic 찾기
```
User ID + 기간
    ↓
goals_id별 그룹화 (NULL은 0L로 매핑)
    ↓
각 주제별 완료율 계산 (현재 vs 이전)
    ↓
완료율 증가량 기준 정렬
    ↓
최소 3개 이상 로그 필터링
    ↓
Top 1 선택
    ↓
goals_id == 0L → "미분류"
goals_id != 0L → DB 조회 → 실제 카테고리명
    ↓
Result: topicName
```

## 테스트 방법

### 1. Insight-svc 재시작
```powershell
cd PlanIt-Insight-svc
./gradlew bootRun
```

### 2. 배치 API 테스트
```powershell
Invoke-WebRequest -Uri "http://localhost:8084/api/v1/batch/generate-report?userId=test-user-001&yearMonth=2026-02" -Method POST
```

### 3. 로그 확인
Insight-svc 로그에서 다음 내용 확인:
```
INFO - Calculating growth rate for user: test-user-001, month: 2026-02
INFO - Current month logs count: 45
INFO - Previous 3 months logs count: 120
INFO - Current completion rate: 67%, Previous completion rate: 55%
INFO - Calculated growth rate: 22%
INFO - Finding top growth topic for user: test-user-001
INFO - Current period - Total categories: 3, Null goals count: 5
INFO - Previous period - Total categories: 3, Null goals count: 8
DEBUG - GoalsId: 101, Current: 75%, Previous: 60%, Growth: 15%
DEBUG - GoalsId: 102, Current: 80%, Previous: 55%, Growth: 25%
DEBUG - GoalsId: 0, Current: 50%, Previous: 45%, Growth: 5%
INFO - Top growth topic: 운동 (goals_id = 102)
INFO - Growth rate calculation completed: {topicName=운동, growthRate=22, previousRate=55, currentRate=67}
```

### 4. DynamoDB 데이터 확인
```powershell
aws dynamodb scan --table-name ai_reports --endpoint-url http://localhost:8001 --filter-expression "report_type = :type" --expression-attribute-values '{":type":{"S":"GROWTH"}}'
```

예상 결과:
```json
{
  "topicName": "운동",
  "growthRate": 22,
  "previousRate": 55,
  "currentRate": 67,
  "message": "이전 3개월 보다 운동 분야에서 22% 성장했어요! 정말 대단한 변화입니다. 꾸준한 노력이 빛을 발하고 있네요 💪"
}
```

## 예상 결과

### Case 1: 특정 카테고리 성장
```json
{
  "topicName": "운동",
  "growthRate": 25,
  "previousRate": 60,
  "currentRate": 75
}
```

### Case 2: 미분류 카테고리 성장
```json
{
  "topicName": "미분류",
  "growthRate": 15,
  "previousRate": 45,
  "currentRate": 52
}
```

### Case 3: 음수 성장
```json
{
  "topicName": "학습",
  "growthRate": -10,
  "previousRate": 70,
  "currentRate": 63
}
```

### Case 4: 데이터 없음
```json
{}
```

## 주요 개선 사항 요약

1. ✅ 하드코딩 완전 제거 (더미 값 -24 제거)
2. ✅ 실제 DB 쿼리 기반 동적 계산
3. ✅ goals_id NULL 처리 명확화 ("미분류"로 표시)
4. ✅ 실제 카테고리명 DB 조회 (Goals + CategoryList 연동)
5. ✅ 상세한 로깅 추가 (디버깅 용이)
6. ✅ 최소 데이터 요구 (3개 이상 로그)
7. ✅ Fallback 로직 (성장 없으면 가장 활발한 주제)
8. ✅ 빈 데이터 처리 (데이터 없으면 빈 결과)

## 참고 사항

- ActionLog 테이블의 `action_type='COMPLETED'` 건수 기반 계산
- goals_id가 NULL인 경우 0L로 매핑하여 "미분류" 처리
- 최소 3개 이상의 로그가 있는 주제만 비교 대상
- DB 조회 실패 시 "전체"로 Fallback
- 로그 레벨을 DEBUG로 설정하면 더 상세한 정보 확인 가능
