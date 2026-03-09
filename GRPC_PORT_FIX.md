# gRPC 서버 포트 설정 수정

## 문제 상황

Schedule-svc에서 Insight-svc의 ActionLog gRPC 서버에 연결 시도 시 다음 에러 발생:

```
Connection refused: getsockopt: localhost/127.0.0.1:9094
```

## 원인

Insight-svc의 `application.yml`에 **gRPC 서버 포트 설정이 누락**되어 있었음.

- Schedule-svc는 9094 포트로 연결 시도
- Insight-svc는 gRPC 서버 포트 설정이 없어서 서버가 시작되지 않음

## 해결 방법

### application.yml에 gRPC 서버 포트 추가

```yaml
# gRPC 설정
grpc:
  # Insight Service 자신의 gRPC 서버 포트 (Schedule-svc에서 ActionLog 기록 시 호출)
  server:
    port: ${GRPC_SERVER_PORT:9094}
  
  # gRPC 클라이언트 설정 (InsightAI-svc Python 서버)
  client:
    chat-service:
      address: ${GRPC_CHAT_SERVICE_ADDRESS:static://localhost:9095}
      # ...
```

## 포트 구성

### Insight-svc 포트

| 포트 | 프로토콜 | 용도 | 환경 변수 |
|------|---------|------|----------|
| 8084 | HTTP | REST API, Swagger UI | `SERVER_PORT` |
| 9094 | gRPC | ActionLog 서버 (Schedule-svc → Insight-svc) | `GRPC_SERVER_PORT` |

### 전체 시스템 포트 맵

```
┌─────────────────────────────────────────────────────────────┐
│ Schedule-svc                                                │
│ - HTTP: 8082                                                │
│ - gRPC Server: 9091                                         │
│ - gRPC Client → Insight-svc:9094 (ActionLog 기록)          │
└─────────────────────────────────────────────────────────────┘
                    ↓ gRPC 호출 (9094)
┌─────────────────────────────────────────────────────────────┐
│ Insight-svc                                                 │
│ - HTTP: 8084                                                │
│ - gRPC Server: 9094 (ActionLog 서비스)                     │
│ - gRPC Client → InsightAI-svc:9095 (Chatbot, Report)      │
└─────────────────────────────────────────────────────────────┘
                    ↓ gRPC 호출 (9095)
┌─────────────────────────────────────────────────────────────┐
│ InsightAI-svc                                               │
│ - HTTP: 8085 (FastAPI)                                     │
│ - gRPC Server: 9095 (Chatbot + Report)                    │
└─────────────────────────────────────────────────────────────┘
```

## 서비스 시작 순서

### 1. Insight-svc 재시작 (필수!)

```bash
cd PlanIt-Insight-svc

# 기존 프로세스 종료
# Ctrl+C 또는 taskkill

# 재시작
./gradlew bootRun
```

시작 로그 확인:
```
gRPC Server started on port 9094
  - ActionLogService registered
```

### 2. Schedule-svc 재시작

```bash
cd PlanIt-Schedule-svc
./gradlew bootRun
```

연결 성공 로그:
```
gRPC client connected to insight-service: static://localhost:9094
```

## 검증 방법

### 1. 포트 리스닝 확인

```bash
# Insight-svc gRPC 서버 확인
netstat -ano | findstr :9094
```

출력 예시:
```
TCP    0.0.0.0:9094           0.0.0.0:0              LISTENING       12345
```

### 2. ActionLog 기록 테스트

Schedule-svc에서 Task 완료/미룸 시도:

```bash
curl -X PUT "http://localhost:8082/api/v1/base/tasks/1/complete" \
  -H "X-User-Id: test-user-001"
```

Insight-svc 로그 확인:
```
[ActionLog] Received COMPLETED: user=test-user-001, task=1
[ActionLog] Successfully saved to database
```

### 3. Health Check

```bash
# Insight-svc
curl http://localhost:8084/api/v1/insight/actuator/health

# Schedule-svc
curl http://localhost:8082/api/v1/base/actuator/health
```

## 문제 해결

### 여전히 Connection refused 발생 시

1. **Insight-svc 로그 확인**
   ```
   gRPC Server started on port 9094
   ```
   이 메시지가 없으면 서버가 시작되지 않은 것

2. **포트 충돌 확인**
   ```bash
   netstat -ano | findstr :9094
   ```
   다른 프로세스가 사용 중이면 종료

3. **방화벽 확인**
   - Windows Defender 방화벽에서 9094 포트 허용

4. **환경 변수 확인**
   ```bash
   # Schedule-svc application.yml
   insight-service:
     address: static://localhost:9094  # 포트 확인!
   ```

### 포트 변경이 필요한 경우

환경 변수로 변경 가능:

```bash
# Insight-svc
export GRPC_SERVER_PORT=9093

# Schedule-svc
export INSIGHT_SERVICE_GRPC_ADDRESS=static://localhost:9093
```

## 수정된 파일

- `PlanIt-Insight-svc/src/main/resources/application.yml`
  - `grpc.server.port` 설정 추가
- `PlanIt-Insight-svc/ENVIRONMENT_VARIABLES.md`
  - `GRPC_SERVER_PORT` 환경 변수 문서화

## 참고

- `GRPC_ACTION_LOG_SERVER_GUIDE.md` - ActionLog gRPC 서버 가이드
- `ENVIRONMENT_VARIABLES.md` - 환경 변수 전체 목록
- Schedule-svc의 `GRPC_ACTION_LOG_INTEGRATION.md` - 클라이언트 통합 가이드

---

**수정 일시**: 2026-03-08  
**상태**: ✅ 완료  
**조치**: Insight-svc 재시작 필요
