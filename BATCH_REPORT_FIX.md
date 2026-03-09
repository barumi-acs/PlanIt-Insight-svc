# 배치 리포트 생성 문제 해결

## 문제 상황
```
2026-03-09 19:17:59 INFO: Starting monthly report generation batch
2026-03-09 19:17:59 INFO: Found 0 active users for monthly reports
2026-03-09 19:17:59 INFO: Monthly report generation completed: success=0, fail=0
```

배치 API(`/api/v1/batch/monthly-reports`, `/api/v1/batch/weekly-reports`)를 실행해도 활성 사용자가 0명으로 조회되어 리포트가 생성되지 않음.

## 원인 분석

### 1. `ReportGenerationScheduler.getActiveUsers()` 메서드 문제
```java
// 수정 전 (문제 코드)
private List<String> getActiveUsers(YearMonth targetMonth, double minAvgTasks) {
    Set<String> allUsers = new HashSet<>();  // ❌ 빈 Set으로 초기화
    // 실제 구현에서는 User Service API를 호출하거나 별도 테이블에서 조회
    // 여기서는 간단히 빈 리스트 반환 (실제 사용자 데이터가 있을 때 동작)
    
    List<String> activeUsers = new ArrayList<>();
    for (String userId : allUsers) {  // ❌ 빈 Set이므로 루프 실행 안 됨
        // ...
    }
    return activeUsers;  // ❌ 항상 빈 리스트 반환
}
```

### 2. ActionLogRepository에 유니크 사용자 조회 메서드 없음
- ActionLog에서 활동한 사용자 목록을 조회하는 메서드가 없었음
- 배치 작업에서 활성 사용자를 찾을 수 없었음

## 해결 방법

### 1. ActionLogRepository에 메서드 추가
```java
/**
 * 특정 기간 내 활동한 모든 유니크한 사용자 ID 조회
 * 배치 작업에서 활성 사용자 목록을 가져올 때 사용
 */
@Query("""
    SELECT DISTINCT a.userId
    FROM ActionLogEntity a
    WHERE a.actionTime BETWEEN :startTime AND :endTime
    """)
List<String> findDistinctUserIdsByActionTimeBetween(
    @Param("startTime") LocalDateTime startTime,
    @Param("endTime") LocalDateTime endTime
);
```

### 2. ReportGenerationScheduler.getActiveUsers() 수정
```java
// 수정 후 (정상 코드)
private List<String> getActiveUsers(YearMonth targetMonth, double minAvgTasks) {
    LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
    LocalDateTime end = targetMonth.atEndOfMonth().atTime(23, 59, 59);
    long days = targetMonth.lengthOfMonth();
    
    // ✅ ActionLog에서 해당 기간에 활동한 모든 유니크한 userId 추출
    List<String> allUsers = actionLogRepository.findDistinctUserIdsByActionTimeBetween(start, end);
    log.debug("Found {} users with activity in {}", allUsers.size(), targetMonth);
    
    List<String> activeUsers = new ArrayList<>();
    for (String userId : allUsers) {
        Double avgTaskCount = actionLogRepository.calculateAverageDailyTaskCount(
            userId, start, end, days
        );
        
        // ✅ 일 평균 Task 수가 minAvgTasks 이상인 사용자만 필터링
        if (avgTaskCount != null && avgTaskCount >= minAvgTasks) {
            activeUsers.add(userId);
            log.debug("User {} is active: avg daily tasks = {}", userId, avgTaskCount);
        } else {
            log.debug("User {} is inactive: avg daily tasks = {}", userId, avgTaskCount);
        }
    }
    
    return activeUsers;
}
```

## 테스트 방법

### 1. DB에 ActionLog 데이터 확인
```sql
-- check_active_users.sql 실행
USE planit_insight_db;

-- 전체 ActionLog 개수 확인
SELECT COUNT(*) as total_logs FROM user_action_logs;

-- 유니크한 사용자 수 확인
SELECT COUNT(DISTINCT user_id) as unique_users FROM user_action_logs;

-- 2026년 3월 활동한 사용자 확인 (일 평균 Task 3개 이상)
SELECT 
    user_id,
    COUNT(*) as log_count,
    COUNT(DISTINCT task_id) as unique_tasks,
    COUNT(DISTINCT task_id) / DAY(LAST_DAY('2026-03-01')) as avg_daily_tasks
FROM user_action_logs
WHERE action_time >= '2026-03-01 00:00:00'
  AND action_time <= '2026-03-31 23:59:59'
GROUP BY user_id
HAVING avg_daily_tasks >= 3.0;
```

### 2. 특정 사용자로 리포트 생성 테스트
```bash
# PowerShell
.\test_batch_report.ps1

# 또는 직접 curl
curl -X POST "http://localhost:8084/api/v1/batch/generate-report?userId=test-user-001&yearMonth=2026-03"
```

### 3. 주간/월간 배치 실행 테스트
```bash
# 주간 리포트 배치
curl -X POST http://localhost:8084/api/v1/batch/weekly-reports

# 월간 리포트 배치
curl -X POST http://localhost:8084/api/v1/batch/monthly-reports
```

## 예상 결과

### 수정 전
```
INFO: Found 0 active users for monthly reports
INFO: Monthly report generation completed: success=0, fail=0
```

### 수정 후 (데이터가 있는 경우)
```
INFO: Starting monthly report generation batch
DEBUG: Found 2 users with activity in 2026-03
DEBUG: User test-user-001 is active: avg daily tasks = 5.2
DEBUG: User friend-user-001 is active: avg daily tasks = 4.8
INFO: Found 2 active users for monthly reports
INFO: Generating monthly report for user: test-user-001
INFO: Successfully saved GROWTH report for user: test-user-001
INFO: Successfully saved TIMELINE report for user: test-user-001
INFO: Successfully saved PATTERN report for user: test-user-001
INFO: Successfully saved SUMMARY report for user: test-user-001
INFO: Generating monthly report for user: friend-user-001
INFO: Successfully saved GROWTH report for user: friend-user-001
INFO: Successfully saved TIMELINE report for user: friend-user-001
INFO: Successfully saved PATTERN report for user: friend-user-001
INFO: Successfully saved SUMMARY report for user: friend-user-001
INFO: Monthly report generation completed: success=2, fail=0
```

## 주의사항

### 1. 활성 사용자 기준
- 일 평균 Task 수가 3개 이상인 사용자만 리포트 생성
- 너무 적은 데이터로는 의미 있는 리포트 생성 불가

### 2. 데이터 준비
- ActionLog 테이블에 최소 1개월치 데이터 필요
- Schedule-svc에서 Task 완료/미룸 시 자동으로 ActionLog 전송됨

### 3. InsightAI-svc 연동
- InsightAI-svc (Python gRPC 서버)가 실행 중이어야 함
- 포트 50051에서 gRPC 서비스 제공 중인지 확인

### 4. DynamoDB 연결
- 로컬 DynamoDB (포트 8001) 또는 AWS DynamoDB 연결 필요
- 리포트 생성 후 DynamoDB에 저장됨

## 변경된 파일

- `src/main/java/com/planit/analytics/repository/ActionLogRepository.java`
  - `findDistinctUserIdsByActionTimeBetween()` 메서드 추가
  
- `src/main/java/com/planit/analytics/scheduler/ReportGenerationScheduler.java`
  - `getActiveUsers()` 메서드 수정 (실제 사용자 조회 로직 구현)
  - 디버그 로그 추가

## 다음 단계

1. DB에 ActionLog 데이터가 충분한지 확인
2. 특정 사용자로 리포트 생성 테스트
3. 주간/월간 배치 실행 테스트
4. InsightAI-svc 로그 확인 (gRPC 요청 수신 여부)

---

**작성일**: 2026-03-09  
**상태**: ✅ 수정 완료 (테스트 필요)
