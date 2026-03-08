# TASK 10: 챗봇 API BFF 패턴 전환 완료

## 작업 개요

챗봇 서비스의 통신 구조를 BFF (Backend For Frontend) 패턴으로 전환하여 아키텍처 일관성과 보안을 강화했습니다.

## 아키텍처 변경

### Before (Deprecated)

```
Frontend → InsightAI-svc (Python REST API)
```

**문제점:**
- Python 서버 외부 직접 노출
- 인증/인가 로직 분산
- MSA 아키텍처 일관성 부족

### After (BFF Pattern)

```
Frontend → Insight-svc (Java BFF) → InsightAI-svc (Python gRPC)
```

**장점:**
- ✅ 단일 진입점 (Java BFF)
- ✅ Python 서버 외부 노출 차단
- ✅ 인증/인가 중앙 집중화
- ✅ MSA 아키텍처 일관성 확보
- ✅ 장애 격리 (Fallback 처리)

## 구현 내용

### 1. Java (Insight-svc) - BFF 구현

#### Proto 파일

- `src/main/proto/chat_service.proto`
  - ChatRequest, ChatResponse 메시지 정의
  - ChatbotService 서비스 정의

#### gRPC 클라이언트

- `ChatGrpcClient.java`
  - `@GrpcClient("chat-service")` 사용
  - Python gRPC 서버 호출
  - 장애 격리 (Fallback 응답)

#### REST API 컨트롤러

- `ChatbotController.java`
  - `POST /api/v1/insight/chat/query`
  - 프론트엔드 요청 수신
  - gRPC 호출 및 응답 변환

#### DTO

- `ChatbotRequestDto.java` - 요청 DTO
- `ChatbotResponseDto.java` - 응답 DTO

### 2. Python (InsightAI-svc) - REST API Deprecated

#### FastAPI 엔드포인트

- `app/api/chatbot.py`
  - `@deprecated` 마킹
  - 경고 로그 추가
  - 하위 호환성 유지

#### gRPC 서버 (변경 없음)

- `app/grpc_server/chatbot_servicer.py`
  - 기존 로직 그대로 사용
  - `main_grpc.py`에서 서비스 제공

### 3. Frontend - API 호출 변경

#### 환경 변수

- `.env.development`, `.env.example`
  - `VITE_INSIGHTAI_SERVICE_URL` Deprecated
  - `VITE_INSIGHT_SERVICE_URL` 사용

#### API 엔드포인트

- 기존: `POST http://localhost:8085/ai/chat/query`
- 신규: `POST http://localhost:8084/api/v1/insight/chat/query`

## API 명세

### Endpoint

```
POST /api/v1/insight/chat/query
```

### Request

```json
{
  "userId": "user-001",
  "query": "지난 주에 어느 요일에 할 일을 가장 많이 미뤘나요?"
}
```

### Response

```json
{
  "answer": "일요일 8건으로 가장 많이 미뤘습니다.\n- 토요일: 5건\n- 금요일: 3건\n주말에 미루는 경향이 있습니다.",
  "sources": ["query_user_action_logs 실행"],
  "generatedAt": "2026-03-08T10:30:00.123456"
}
```

### Error Response (Fallback)

```json
{
  "answer": "죄송합니다. 일시적으로 AI 챗봇 서비스에 접속할 수 없습니다. 잠시 후 다시 시도해주세요.",
  "sources": ["Fallback"],
  "generatedAt": "2026-03-08T10:30:00Z"
}
```

## 통신 흐름

```
1. Frontend
   ↓ POST /api/v1/insight/chat/query (JSON)
   
2. Insight-svc (Java BFF)
   - ChatbotController: REST 요청 수신
   - ChatGrpcClient: gRPC 메시지 변환
   ↓ gRPC QueryChatbot (Protobuf)
   
3. InsightAI-svc (Python)
   - ChatServiceServicer: gRPC 요청 수신
   - ChatbotService: 비즈니스 로직 처리
   - Bedrock API: AI 답변 생성
   ↑ gRPC ChatResponse (Protobuf)
   
4. Insight-svc (Java BFF)
   - DTO 변환
   ↑ JSON Response
   
5. Frontend
   - 답변 표시
```

## 장애 격리 (Fallback)

Python AI 서버 다운 시 Java BFF에서 Fallback 응답 반환:

```java
private ChatResponse createFallbackResponse(String userId, String query) {
    return ChatResponse.newBuilder()
            .setAnswer("죄송합니다. 일시적으로 AI 챗봇 서비스에 접속할 수 없습니다...")
            .addSources("Fallback")
            .setGeneratedAt(Instant.now().toString())
            .build();
}
```

## 생성된 파일

### Java (Insight-svc)

- `src/main/proto/chat_service.proto` - gRPC 프로토콜 정의
- `src/main/java/com/planit/analytics/grpc/ChatGrpcClient.java` - gRPC 클라이언트
- `src/main/java/com/planit/analytics/controller/ChatbotController.java` - REST API 컨트롤러
- `src/main/java/com/planit/analytics/dto/ChatbotRequestDto.java` - 요청 DTO
- `src/main/java/com/planit/analytics/dto/ChatbotResponseDto.java` - 응답 DTO
- `CHATBOT_BFF_MIGRATION.md` - 마이그레이션 가이드

### Python (InsightAI-svc)

- `app/api/chatbot.py` - FastAPI Deprecated 처리

### Frontend

- `.env.development` - 환경 변수 업데이트
- `.env.example` - 환경 변수 예시 업데이트
- `CHATBOT_API_MIGRATION.md` - 프론트엔드 마이그레이션 가이드

## 테스트 방법

### 1. 서비스 시작

```bash
# InsightAI gRPC 서버
cd PlanIt-InsightAI-svc
python -m app.main_grpc

# Insight Java 서버
cd PlanIt-Insight-svc
./gradlew bootRun
```

### 2. API 테스트

```bash
curl -X POST "http://localhost:8084/api/v1/insight/chat/query" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user-001",
    "query": "지난 주에 어느 요일에 할 일을 가장 많이 미뤘나요?"
  }'
```

### 3. Swagger UI

- Java BFF: http://localhost:8084/swagger-ui.html
- Python (Deprecated): http://localhost:8085/docs

## 마이그레이션 체크리스트

### Backend

- [x] Java proto 파일 추가
- [x] Java gRPC 클라이언트 구현
- [x] Java REST API 컨트롤러 구현
- [x] Java DTO 정의
- [x] Python FastAPI Deprecated 처리
- [x] 장애 격리 (Fallback) 구현
- [x] 문서 작성

### Frontend

- [x] 환경 변수 업데이트
- [x] 마이그레이션 가이드 작성
- [ ] API 호출 코드 수정 (프론트엔드 팀 작업 필요)
- [ ] 테스트 및 검증

## 주요 개선 사항

### 1. 보안 강화

- Python 서버 외부 노출 차단
- Java BFF에서 인증/인가 중앙 집중화
- CORS 정책 통일

### 2. 성능 향상

- gRPC HTTP/2 멀티플렉싱
- Protobuf 바이너리 직렬화 (JSON 대비 3-10배 빠름)

### 3. 아키텍처 일관성

- MSA 내부 통신 표준 (gRPC) 준수
- BFF 패턴 적용으로 프론트엔드 친화적 API 제공

### 4. 장애 격리

- Python AI 서버 다운 시에도 서비스 중단 방지
- Fallback 응답으로 사용자 경험 유지

## 다음 단계

1. 프론트엔드 팀과 협업하여 API 호출 코드 수정
2. 통합 테스트 수행
3. 프로덕션 배포 전 성능 테스트
4. 모니터링 및 로깅 강화

## 참고 문서

- `CHATBOT_BFF_MIGRATION.md` - 상세 마이그레이션 가이드
- `GRPC_UNIFIED_ARCHITECTURE.md` - gRPC 통합 아키텍처
- `ENVIRONMENT_VARIABLES.md` - 환경 변수 가이드
- Frontend: `CHATBOT_API_MIGRATION.md` - 프론트엔드 마이그레이션 가이드

---

**작업 완료 일시**: 2026-03-08  
**상태**: ✅ 완료  
**영향 범위**: Frontend, Insight-svc, InsightAI-svc  
**다음 작업**: 프론트엔드 API 호출 코드 수정
