# 챗봇 API BFF 패턴 마이그레이션 가이드

## 개요

챗봇 서비스의 통신 구조를 BFF (Backend For Frontend) 패턴으로 전환했습니다.

## 아키텍처 변경

### 기존 아키텍처 (Deprecated)

```
┌─────────────┐
│  Frontend   │
└──────┬──────┘
       │ REST API (직접 호출)
       │ POST http://localhost:8085/ai/chat/query
       ↓
┌─────────────────────────────────┐
│  InsightAI-svc (Python)         │
│  - FastAPI REST API (8085)      │
│  - gRPC Server (50051)          │
└─────────────────────────────────┘
```

**문제점:**
- Python 서버가 외부에 직접 노출
- 인증/인가 로직 분산
- 보안 취약점 증가
- MSA 아키텍처 일관성 부족

### 신규 아키텍처 (BFF 패턴)

```
┌─────────────┐
│  Frontend   │
└──────┬──────┘
       │ REST API
       │ POST http://localhost:8084/api/v1/insight/chat/query
       ↓
┌─────────────────────────────────┐
│  Insight-svc (Java BFF)         │
│  - REST API Gateway (8084)      │
│  - gRPC Client                  │
└──────┬──────────────────────────┘
       │ gRPC (내부 통신)
       │ Port 50051
       ↓
┌─────────────────────────────────┐
│  InsightAI-svc (Python)         │
│  - gRPC Server (50051)          │
│  - FastAPI (8085, Deprecated)   │
└─────────────────────────────────┘
```

**장점:**
- ✅ 단일 진입점 (Java BFF)
- ✅ Python 서버 외부 노출 차단
- ✅ 인증/인가 중앙 집중화
- ✅ MSA 아키텍처 일관성 확보
- ✅ 장애 격리 (Fallback 처리)

## API 변경 사항

### 엔드포인트 변경

| 항목 | 기존 (Deprecated) | 신규 (BFF) |
|------|------------------|-----------|
| Base URL | `http://localhost:8085` | `http://localhost:8084` |
| Path | `/ai/chat/query` | `/api/v1/insight/chat/query` |
| Method | POST | POST |
| Protocol | REST (직접) | REST → gRPC (내부) |

### 요청/응답 포맷 (동일)

**Request:**
```json
{
  "userId": "user-001",
  "query": "지난 주에 어느 요일에 할 일을 가장 많이 미뤘나요?"
}
```

**Response:**
```json
{
  "answer": "일요일 8건으로 가장 많이 미뤘습니다.\n- 토요일: 5건\n- 금요일: 3건\n주말에 미루는 경향이 있습니다.",
  "sources": ["query_user_action_logs 실행"],
  "generatedAt": "2026-03-08T10:30:00.123456"
}
```

## 프론트엔드 마이그레이션

### 1. 환경 변수 업데이트

#### .env.development

```bash
# 기존 (Deprecated)
# VITE_INSIGHTAI_SERVICE_URL=http://localhost:8085

# 신규 (BFF)
VITE_INSIGHT_SERVICE_URL=http://localhost:8084
```

### 2. API 호출 코드 수정

#### 기존 코드 (Deprecated)

```typescript
// ❌ 직접 Python 서버 호출
const response = await fetch(`${import.meta.env.VITE_INSIGHTAI_SERVICE_URL}/ai/chat/query`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    userId: 'user-001',
    query: '지난 주에 어느 요일에 할 일을 가장 많이 미뤘나요?'
  })
});
```

#### 신규 코드 (BFF)

```typescript
// ✅ Java BFF를 통한 호출
const response = await fetch(`${import.meta.env.VITE_INSIGHT_SERVICE_URL}/api/v1/insight/chat/query`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    userId: 'user-001',
    query: '지난 주에 어느 요일에 할 일을 가장 많이 미뤘나요?'
  })
});
```

### 3. API 서비스 클래스 예시

```typescript
// src/services/chatbotService.ts

const INSIGHT_BASE_URL = import.meta.env.VITE_INSIGHT_SERVICE_URL;

export interface ChatbotRequest {
  userId: string;
  query: string;
}

export interface ChatbotResponse {
  answer: string;
  sources: string[];
  generatedAt: string;
}

export const chatbotService = {
  /**
   * 챗봇 질의
   * 
   * @param request 챗봇 질의 요청
   * @returns ChatbotResponse AI 생성 답변
   */
  async query(request: ChatbotRequest): Promise<ChatbotResponse> {
    const response = await fetch(`${INSIGHT_BASE_URL}/api/v1/insight/chat/query`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error(`Chatbot query failed: ${response.statusText}`);
    }

    return response.json();
  },
};
```

## 백엔드 구현 상세

### Java (Insight-svc)

#### 1. Proto 파일

`src/main/proto/chat_service.proto`

```protobuf
syntax = "proto3";

package com.planit.analytics.grpc;

message ChatRequest {
  string user_id = 1;
  string query = 2;
}

message ChatResponse {
  string answer = 1;
  repeated string sources = 2;
  string generated_at = 3;
}

service ChatbotService {
  rpc QueryChatbot(ChatRequest) returns (ChatResponse);
}
```

#### 2. gRPC 클라이언트

`ChatGrpcClient.java`

```java
@Service
public class ChatGrpcClient {
    @GrpcClient("chat-service")
    private ChatbotServiceGrpc.ChatbotServiceBlockingStub chatbotStub;

    public ChatResponse queryChatbot(String userId, String query) {
        ChatRequest request = ChatRequest.newBuilder()
                .setUserId(userId)
                .setQuery(query)
                .build();
        
        return chatbotStub.queryChatbot(request);
    }
}
```

#### 3. REST API 컨트롤러

`ChatbotController.java`

```java
@RestController
@RequestMapping("/api/v1/insight/chat")
public class ChatbotController {
    private final ChatGrpcClient chatGrpcClient;

    @PostMapping("/query")
    public ResponseEntity<ChatbotResponseDto> queryChatbot(
            @Valid @RequestBody ChatbotRequestDto request
    ) {
        ChatResponse grpcResponse = chatGrpcClient.queryChatbot(
                request.getUserId(),
                request.getQuery()
        );
        
        ChatbotResponseDto response = ChatbotResponseDto.builder()
                .answer(grpcResponse.getAnswer())
                .sources(grpcResponse.getSourcesList())
                .generatedAt(grpcResponse.getGeneratedAt())
                .build();
        
        return ResponseEntity.ok(response);
    }
}
```

### Python (InsightAI-svc)

#### FastAPI 엔드포인트 Deprecated 처리

`app/api/chatbot.py`

```python
@router.post("/query", response_model=ChatQueryResponse, deprecated=True)
async def query_chatbot(request: ChatQueryRequest):
    """
    ⚠️ DEPRECATED: Java BFF를 사용하세요
    - 신규: POST http://localhost:8084/api/v1/insight/chat/query
    """
    logger.warning("[DEPRECATED] Direct REST API call detected")
    # ... 기존 로직 유지 (하위 호환성)
```

#### gRPC 서버 (변경 없음)

`app/grpc_server/chatbot_servicer.py`

```python
class ChatServiceServicer(chat_service_pb2_grpc.ChatbotServiceServicer):
    async def QueryChatbot(self, request, context):
        # 기존 로직 그대로 사용
        result = await self.chatbot_service.process_query(
            user_id=request.user_id,
            query=request.query
        )
        return chat_service_pb2.ChatResponse(...)
```

## 테스트 방법

### 1. 서비스 시작

```bash
# 1. InsightAI gRPC 서버
cd PlanIt-InsightAI-svc
python -m app.main_grpc

# 2. Insight Java 서버
cd PlanIt-Insight-svc
./gradlew bootRun
```

### 2. API 테스트

```bash
# 신규 BFF 엔드포인트 테스트
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

## 장애 격리 (Fallback)

Python AI 서버가 다운되어도 Java BFF는 Fallback 응답을 반환합니다:

```json
{
  "answer": "죄송합니다. 일시적으로 AI 챗봇 서비스에 접속할 수 없습니다. 잠시 후 다시 시도해주세요.",
  "sources": ["Fallback"],
  "generatedAt": "2026-03-08T10:30:00Z"
}
```

## 마이그레이션 체크리스트

### 프론트엔드

- [ ] 환경 변수 업데이트 (`.env.development`, `.env.production`)
- [ ] API 호출 URL 변경 (`8085` → `8084`)
- [ ] API Path 변경 (`/ai/chat/query` → `/api/v1/insight/chat/query`)
- [ ] 테스트 및 검증

### 백엔드

- [x] Java proto 파일 추가
- [x] Java gRPC 클라이언트 구현
- [x] Java REST API 컨트롤러 구현
- [x] Python FastAPI Deprecated 처리
- [x] 장애 격리 (Fallback) 구현

## 롤백 계획

문제 발생 시 기존 방식으로 롤백 가능:

1. 프론트엔드 환경 변수 원복
2. Python FastAPI 엔드포인트 활성화 (deprecated 제거)
3. 서비스 재시작

## 참고 문서

- `GRPC_UNIFIED_ARCHITECTURE.md` - gRPC 통합 아키텍처
- `ENVIRONMENT_VARIABLES.md` - 환경 변수 가이드
- `CHATBOT_RESPONSE_STYLE.md` - 챗봇 응답 스타일

---

**마이그레이션 완료 일시**: 2026-03-08  
**상태**: ✅ 완료  
**영향 범위**: Frontend, Insight-svc, InsightAI-svc
