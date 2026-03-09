# Insight Service - Action Log gRPC Server 구현 가이드

## 📋 개요

Schedule Service로부터 사용자 행동 로그(완료/미루기/삭제)를 수신하여 `user_action_logs` 테이블에 저장하는 gRPC 서버 구현 가이드입니다.

## 🎯 요구사항

1. **비동기 수신**: Schedule Service의 비동기 호출을 빠르게 응답
2. **데이터 저장**: `user_action_logs` 테이블에 INSERT
3. **예외 처리**: 저장 실패 시에도 Schedule Service에 성공 응답 (로그만 남김)
4. **포트 설정**: gRPC 서버 포트 9094

## 📁 필요한 파일

### 1. Proto 파일 (이미 생성됨)
- `src/main/proto/action_log_service.proto`

### 2. 구현 필요 파일
- `src/main/java/com/planit/analytics/grpc/ActionLogServiceImpl.java` (gRPC 서버)
- `src/main/java/com/planit/analytics/repository/UserActionLogRepository.java` (JPA Repository)
- `src/main/java/com/planit/analytics/entity/UserActionLog.java` (Entity)
- `src/main/resources/application.yml` (gRPC 서버 포트 설정)

## 🔧 구현 예시

### 1. Entity: UserActionLog.java

```java
package com.planit.analytics.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_action_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "goals_id", nullable = false)
    private Long goalsId;

    @Column(name = "action_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "postponed_to_date")
    private LocalDate postponedToDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum ActionType {
        COMPLETED,
        POSTPONED,
        DELETED
    }
}
```

### 2. Repository: UserActionLogRepository.java

```java
package com.planit.analytics.repository;

import com.planit.analytics.entity.UserActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {
    // 기본 CRUD 메서드만 사용
}
```

### 3. gRPC Service: ActionLogServiceImpl.java

```java
package com.planit.analytics.grpc;

import com.planit.analytics.entity.UserActionLog;
import com.planit.analytics.repository.UserActionLogRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDate;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ActionLogServiceImpl extends ActionLogServiceGrpc.ActionLogServiceImplBase {

    private final UserActionLogRepository actionLogRepository;

    @Override
    public void recordActionLog(ActionLogRequest request, StreamObserver<ActionLogResponse> responseObserver) {
        log.info("[gRPC ActionLog] Received action log: user={}, task={}, goals={}, action={}, dueDate={}, postponedTo={}",
                request.getUserId(),
                request.getTaskId(),
                request.getGoalsId(),
                request.getActionType(),
                request.getDueDate(),
                request.getPostponedToDate());

        try {
            // 1️⃣ Entity 생성
            UserActionLog.ActionType actionType = UserActionLog.ActionType.valueOf(request.getActionType());
            LocalDate dueDate = LocalDate.parse(request.getDueDate());
            LocalDate postponedToDate = request.getPostponedToDate().isEmpty() 
                    ? null 
                    : LocalDate.parse(request.getPostponedToDate());

            UserActionLog log = UserActionLog.builder()
                    .userId(request.getUserId())
                    .taskId(request.getTaskId())
                    .goalsId(request.getGoalsId())
                    .actionType(actionType)
                    .dueDate(dueDate)
                    .postponedToDate(postponedToDate)
                    .build();

            // 2️⃣ DB 저장
            UserActionLog saved = actionLogRepository.save(log);

            // 3️⃣ 성공 응답
            ActionLogResponse response = ActionLogResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Action log recorded successfully")
                    .setLogId(saved.getLogId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("[gRPC ActionLog] Successfully saved log_id={}", saved.getLogId());

        } catch (Exception e) {
            // 4️⃣ 예외 발생 시에도 성공 응답 (Schedule Service 롤백 방지)
            log.error("[gRPC ActionLog] Failed to save action log, but returning success to prevent rollback", e);

            ActionLogResponse response = ActionLogResponse.newBuilder()
                    .setSuccess(true) // ⚠️ 의도적으로 true 반환
                    .setMessage("Action log received (save failed internally)")
                    .setLogId(0L)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
```

### 4. application.yml 설정

```yaml
# ──────────────────────────────────────────────────────────────────
# gRPC 설정
# ──────────────────────────────────────────────────────────────────
grpc:
  # Insight Service 자신의 gRPC 서버 포트
  server:
    port: ${GRPC_SERVER_PORT:9094}

  # 외부 gRPC 서비스 클라이언트 설정 (Python InsightAI Service)
  client:
    insight-ai-service:
      address: ${INSIGHT_AI_SERVICE_GRPC_ADDRESS:static://localhost:9095}
      negotiation-type: plaintext
      deadline: 60s  # AWS Bedrock 호출 시간 고려
```

## 🚀 빌드 및 실행

### 1. Proto 컴파일

Gradle이 자동으로 처리하지만, 수동 컴파일이 필요한 경우:

```bash
./gradlew generateProto
```

### 2. 서비스 실행

```bash
./gradlew bootRun
```

### 3. 로그 확인

```
[gRPC ActionLog] Received action log: user=user123, task=456, goals=789, action=COMPLETED, dueDate=2026-03-06, postponedTo=
[gRPC ActionLog] Successfully saved log_id=1001
```

## 🧪 테스트

### 1. grpcurl로 직접 테스트

```bash
# 설치 (Windows)
choco install grpcurl

# 서비스 목록 확인
grpcurl -plaintext localhost:9094 list

# 메서드 호출
grpcurl -plaintext -d '{
  "user_id": "test-user",
  "task_id": 123,
  "goals_id": 456,
  "action_type": "COMPLETED",
  "due_date": "2026-03-06",
  "postponed_to_date": ""
}' localhost:9094 ActionLogService/RecordActionLog
```

### 2. Schedule Service 통합 테스트

```bash
# Schedule Service 실행 (포트 8082)
cd PlanIt-Schedule-svc
./gradlew bootRun

# Insight Service 실행 (포트 8085, gRPC 9094)
cd PlanIt-Insight-svc
./gradlew bootRun

# 할 일 완료 API 호출
curl -X POST http://localhost:8082/api/v1/tasks/123/complete

# Insight Service DB 확인
SELECT * FROM user_action_logs ORDER BY created_at DESC LIMIT 10;
```

## 📊 데이터 흐름

```
[Schedule Service]
    TaskService.toggleComplete()
         ↓
    @Transactional 커밋
         ↓
    UserActionLogGrpcClient.recordCompletedAction()
         ↓ @Async (별도 스레드)
         ↓
    gRPC 호출 (3초 타임아웃)
         ↓
[Insight Service]
    ActionLogServiceImpl.recordActionLog()
         ↓
    UserActionLogRepository.save()
         ↓
    user_action_logs 테이블 INSERT
         ↓
    성공 응답 반환
```

## ⚠️ 중요 설계 결정

### 1. 예외 발생 시에도 성공 응답

```java
catch (Exception e) {
    log.error("Failed to save action log, but returning success", e);
    
    // ⚠️ 의도적으로 success=true 반환
    ActionLogResponse response = ActionLogResponse.newBuilder()
            .setSuccess(true)
            .setMessage("Action log received (save failed internally)")
            .build();
    
    responseObserver.onNext(response);
    responseObserver.onCompleted();
}
```

**이유**: Schedule Service의 메인 트랜잭션(할 일 상태 변경)이 롤백되는 것을 방지하기 위함

### 2. 비동기 처리의 장점

- Schedule Service는 gRPC 응답을 기다리지 않음
- Insight Service가 느리거나 죽어있어도 사용자 경험에 영향 없음
- 행동 로그는 "Best Effort" 방식으로 수집

### 3. 데이터 일관성 vs 가용성

- **선택**: 가용성 우선 (Schedule Service는 항상 정상 동작)
- **트레이드오프**: 일부 행동 로그가 누락될 수 있음
- **보완**: 배치 작업으로 누락된 로그 복구 가능 (선택사항)

## 🔍 트러블슈팅

### Proto 컴파일 오류

```
error: cannot find symbol
  symbol:   class ActionLogServiceGrpc
```

**해결**: Gradle 빌드 실행
```bash
./gradlew clean build
```

### gRPC 서버 포트 충돌

```
io.grpc.netty.shaded.io.netty.channel.unix.Errors$NativeIoException: 
bind(..) failed: Address already in use
```

**해결**: 포트 9094를 사용 중인 프로세스 종료 또는 포트 변경
```bash
# Windows
netstat -ano | findstr :9094
taskkill /PID <PID> /F

# application.yml
grpc:
  server:
    port: 9093  # 다른 포트로 변경
```

### DB 연결 실패

```
com.mysql.cj.jdbc.exceptions.CommunicationsException: 
Communications link failure
```

**해결**: MariaDB 실행 확인 및 application.yml 설정 확인

## 📚 다음 단계

1. ✅ Proto 파일 생성 완료
2. ✅ Schedule Service gRPC Client 구현 완료
3. ⏳ Insight Service gRPC Server 구현 (이 가이드 참고)
4. ⏳ 통합 테스트 및 로그 확인
5. ⏳ 프로덕션 배포 (환경변수 설정)

## 🌐 환경변수 설정 (프로덕션)

```bash
# Insight Service
export GRPC_SERVER_PORT=9094
export SPRING_DATASOURCE_URL=jdbc:mariadb://prod-db:3306/planit_insight_db
export SPRING_DATASOURCE_USERNAME=insight_user
export SPRING_DATASOURCE_PASSWORD=<secure-password>

# Schedule Service
export INSIGHT_SERVICE_GRPC_ADDRESS=static://insight-service:9094
```

## 📖 참고 문서

- [gRPC Spring Boot Starter](https://github.com/grpc-ecosystem/grpc-spring)
- [Protocol Buffers Guide](https://protobuf.dev/programming-guides/proto3/)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
