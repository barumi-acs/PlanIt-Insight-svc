# PlanIt Insight Service - API 명세서

## 서비스 개요

PlanIt Insight Service는 사용자의 할 일 관리 데이터를 분석하여 AI 기반 인사이트와 피드백을 제공하는 백엔드 서비스입니다.

### 기술 스택
- Java 17
- Spring Boot 3.x
- gRPC (내부 통신)
- DynamoDB (데이터 저장소)
- AWS Bedrock (AI 모델)

### 서비스 포트
- HTTP API: `8084`
- gRPC Server: `9094`

### Swagger UI
- URL: http://localhost:8084/swagger-ui.html
- API 문서 및 테스트 인터페이스 제공

---

## 아키텍처

### BFF (Backend For Frontend) 패턴
```
Frontend → Insight-svc (Java BFF) → InsightAI-svc (Python gRPC)
```

### 내부 서비스 통신
```
Schedule-svc → Insight-svc (gRPC ActionLog)
Insight-svc → InsightAI-svc (gRPC Chatbot/Report)
```

---

## API 엔드포인트

### 1. Feedback API
사용자 피드백 및 AI 리포트 조회

#### 1.1 일간 응원 피드백 조회
홈 화면 상단에 표시할 오늘의 요일별 AI 응원 메시지를 조회합니다.

**Endpoint**
```
GET /api/v1/feedbacks/daily-cheer
```

**Request Headers**
```
X-User-Id: string (required) - 사용자 ID (JWT에서 추출)
```

**Response**
```json
{
  "success": true,
  "data": {
    "message": "월요일 화이팅! 이번 주도 잘 해낼 수 있어요.",
    "dayOfWeek": "MONDAY",
    "performanceRate": 75.5,
    "comparisonToAverage": 5.2
  },
  "error": null
}
```

**Response 필드**
- `message`: AI 생성 응원 메시지
- `dayOfWeek`: 요일 (MONDAY ~ SUNDAY)
- `performanceRate`: 오늘 수행률 (%)
- `comparisonToAverage`: 평균 대비 차이 (%)

**Status Codes**
- `200 OK`: 조회 성공
- `400 Bad Request`: 잘못된 요청 파라미터
- `401 Unauthorized`: 인증되지 않은 사용자

---

#### 1.2 AI 피드백 대시보드 조회
리포트 탭에서 표시할 4가지 피드백을 한 번에 조회합니다.

**Endpoint**
```
GET /api/v1/feedbacks/dashboard
```

**Request Headers**
```
X-User-Id: string (required) - 사용자 ID
```

**Query Parameters**
```
yearMonth: string (required) - 대상 월 (예: 2026-02)
week: integer (required) - 대상 주차 (예: 9)
```

**Request Example**
```
GET /api/v1/feedbacks/dashboard?yearMonth=2026-02&week=9
X-User-Id: user-001
```

**Response**
```json
{
  "success": true,
  "data": {
    "growth": {
      "type": "GROWTH",
      "content": "이번 주 '운동' 카테고리에서 24% 성장했어요!",
      "topicName": "운동",
      "growthRate": 24.0,
      "generatedAt": "2026-02-15T10:30:00"
    },
    "timeline": {
      "type": "TIMELINE",
      "content": "오전 9시~11시에 가장 생산적이에요.",
      "peakHours": ["09:00", "10:00", "11:00"],
      "generatedAt": "2026-02-15T10:30:00"
    },
    "pattern": {
      "type": "PATTERN",
      "content": "금요일에 미루는 경향이 있어요.",
      "postponeDay": "FRIDAY",
      "postponeRate": 35.5,
      "generatedAt": "2026-02-15T10:30:00"
    },
    "summary": {
      "type": "SUMMARY",
      "content": "이번 주 80% 달성! 꾸준히 잘하고 있어요.",
      "completionRate": 80.0,
      "totalTasks": 20,
      "completedTasks": 16,
      "generatedAt": "2026-02-15T10:30:00"
    }
  },
  "error": null
}
```

**Response 필드**
- `growth`: 성장 격려 피드백 (가장 성장한 카테고리)
- `timeline`: 타임라인 분석 (생산성 높은 시간대)
- `pattern`: 미룸 패턴 분석 (미루는 요일/시간)
- `summary`: 종합 피드백 (전체 달성률 요약)

**Status Codes**
- `200 OK`: 조회 성공
- `400 Bad Request`: 잘못된 요청 파라미터
- `401 Unauthorized`: 인증되지 않은 사용자
- `404 Not Found`: 분석할 통계 데이터 부족 (IS4041)

---

### 2. Chatbot API
AI 챗봇 질의응답 (BFF 패턴)

#### 2.1 챗봇 질의
사용자의 질의를 AI 챗봇에 전달하여 답변을 받습니다.

**Endpoint**
```
POST /api/v1/insight/chat/query
```

**Request Body**
```json
{
  "query": "이번 주 가장 많이 완료한 카테고리는?"
}
```

**Request 필드**
- `query` (required): 사용자 질의 내용

**Response**
```json
{
  "answer": "이번 주 '운동' 카테고리를 가장 많이 완료했어요. 총 12개 완료했습니다.",
  "sources": [
    "action_log_2026_02",
    "goals_운동"
  ],
  "generatedAt": "2026-02-15T14:30:00Z"
}
```

**Response 필드**
- `answer`: AI 생성 답변
- `sources`: 데이터 출처 목록
- `generatedAt`: 응답 생성 시각 (ISO 8601)

**처리 흐름**
1. REST API 요청 수신 (JSON)
2. 사용자 ID 추출 (현재: 더미 값, Phase 2: JWT에서 추출)
3. gRPC 메시지로 변환
4. Python AI 서버 호출 (gRPC)
5. gRPC 응답을 JSON으로 변환
6. 프론트엔드로 반환

**인증**
- Phase 1 (현재): 인증 우회 (테스트용)
- Phase 2 (예정): JWT 토큰 필수

**Status Codes**
- `200 OK`: 질의 성공
- `400 Bad Request`: 잘못된 요청 데이터
- `401 Unauthorized`: 인증 실패 (Phase 2)
- `500 Internal Server Error`: 서버 내부 에러

**보안**
- Java BFF를 통한 단일 진입점
- Python 서버는 외부 노출 차단
- CORS 설정으로 허용된 Origin만 접근 가능
- Phase 2에서 JWT 기반 인증/인가 적용 예정

---

### 3. Batch API
배치 작업 수동 실행 (개발/테스트용)

#### 3.1 주간 리포트 생성 배치
스케줄러를 기다리지 않고 즉시 주간 리포트를 생성합니다.

**Endpoint**
```
POST /api/v1/batch/weekly-reports
```

**Request**
```
POST /api/v1/batch/weekly-reports
```

**Response**
```json
{
  "success": true,
  "data": {
    "message": "주간 리포트 생성 배치가 실행되었습니다.",
    "timestamp": 1709625000000
  },
  "error": null
}
```

**Status Codes**
- `200 OK`: 배치 실행 성공

---

#### 3.2 월간 리포트 생성 배치
스케줄러를 기다리지 않고 즉시 월간 리포트를 생성합니다.

**Endpoint**
```
POST /api/v1/batch/monthly-reports
```

**Request**
```
POST /api/v1/batch/monthly-reports
```

**Response**
```json
{
  "success": true,
  "data": {
    "message": "월간 리포트 생성 배치가 실행되었습니다.",
    "timestamp": 1709625000000
  },
  "error": null
}
```

**Status Codes**
- `200 OK`: 배치 실행 성공

---

#### 3.3 특정 사용자 리포트 생성
특정 사용자의 리포트를 즉시 생성합니다 (테스트용).

**Endpoint**
```
POST /api/v1/batch/generate-report
```

**Query Parameters**
```
userId: string (required) - 사용자 ID
yearMonth: string (optional) - 대상 월 (예: 2026-02, 기본값: 현재 월)
```

**Request Example**
```
POST /api/v1/batch/generate-report?userId=user-001&yearMonth=2026-02
```

**Response**
```json
{
  "success": true,
  "data": {
    "message": "리포트 생성이 완료되었습니다.",
    "userId": "user-001",
    "yearMonth": "2026-02",
    "timestamp": 1709625000000
  },
  "error": null
}
```

**생성되는 리포트 타입**
- `GROWTH`: 성장 격려
- `TIMELINE`: 타임라인 분석
- `PATTERN`: 미룸 패턴
- `SUMMARY`: 종합 피드백

**Status Codes**
- `200 OK`: 리포트 생성 성공

---

### 4. Internal Action Log API
내부 서비스 간 통신용 (Schedule-svc → Insight-svc)

#### 4.1 Action Log 저장
Task Service에서 사용자의 할 일 처리 로그를 전송받아 저장합니다.

**Endpoint**
```
POST /internal/api/v1/action-logs
```

**Request Body**
```json
{
  "userId": "user-001",
  "taskId": 12345,
  "goalsId": 1,
  "actionType": "COMPLETED",
  "actionTime": "2026-02-15T14:30:00",
  "dueDate": "2026-02-15",
  "postponedToDate": null
}
```

**Request 필드**
- `userId` (required): 사용자 ID
- `taskId` (required): 할 일 ID
- `goalsId` (optional): 카테고리 ID (null 가능)
- `actionType` (required): 행동 타입
  - `COMPLETED`: 완료
  - `POSTPONED`: 미루기
  - `DELETED`: 삭제
- `actionTime` (required): 행동 발생 시각
- `dueDate` (required): 원래 마감일
- `postponedToDate` (optional): 미룬 날짜 (POSTPONED일 때만)

**Response**
```json
{
  "success": true,
  "data": {
    "logId": "log-uuid-12345",
    "userId": "user-001",
    "taskId": 12345,
    "actionType": "COMPLETED",
    "createdAt": "2026-02-15T14:30:00"
  },
  "error": null
}
```

**Status Codes**
- `200 OK`: 저장 성공
- `400 Bad Request`: 잘못된 요청 데이터
- `500 Internal Server Error`: 서버 내부 에러

**참고**
- 이 API는 내부 서비스 간 통신용입니다
- 외부에서 직접 호출하지 않습니다
- gRPC 버전도 제공됩니다 (포트 9094)

---

### 5. Test API
서비스 실행 테스트 및 예시 (개발용)

#### 5.1 기본 테스트
서비스 정상 작동 확인

**Endpoint**
```
GET /api/v1/base/test
```

**Response**
```json
{
  "success": true,
  "data": "PlanIt base 템플릿",
  "error": null
}
```

---

#### 5.2 에러 테스트
글로벌 에러 핸들링 테스트

**Endpoint**
```
GET /api/v1/base/error-test
```

**Response**
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C4041",
    "message": "리소스를 찾을 수 없습니다"
  }
}
```

---

### 6. Sample API
샘플 CRUD 예시 (개발 참고용, 삭제 예정)

#### 6.1 샘플 생성
```
GET /sample/create?name=jybill01
```

#### 6.2 샘플 수정
```
GET /sample/update/{id}?newName=업데이트완료
```

#### 6.3 샘플 삭제
```
GET /sample/delete/{id}
```

---

## 공통 응답 형식

### 성공 응답
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

### 에러 응답
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "에러 메시지"
  }
}
```

---

## 에러 코드

### 공통 에러 코드
- `C4001`: 잘못된 요청 파라미터입니다
- `C4011`: 인증 토큰이 만료되었습니다
- `C4012`: 인증되지 않은 사용자입니다
- `C4031`: 접근 권한이 없습니다
- `C4041`: 요청한 리소스를 찾을 수 없습니다
- `C4051`: 허용되지 않은 메서드입니다
- `C5001`: 서버 내부 에러가 발생했습니다

### Insight Service 에러 코드
- `IS4041`: 분석할 통계 데이터가 부족합니다
- `IS5001`: 통계 분석 중 오류가 발생했습니다
- `IS5002`: 리포트 저장 중 오류가 발생했습니다

### AI Service 에러 코드
- `AI5001`: AI 리포트 생성 중 오류가 발생했습니다
- `AI5002`: AI 서비스 응답 시간이 초과되었습니다

---

## gRPC 서비스

### ActionLog Service (포트 9094)
Schedule-svc에서 호출하는 내부 gRPC 서비스

**Proto 정의**
```protobuf
service ActionLogService {
  rpc RecordAction(ActionLogRequest) returns (ActionLogResponse);
}
```

**사용 예시**
```java
// Schedule-svc에서 호출
ActionLogResponse response = actionLogStub.recordAction(
  ActionLogRequest.newBuilder()
    .setUserId("user-001")
    .setTaskId(12345)
    .setActionType("COMPLETED")
    .build()
);
```

---

## 환경 변수

### 필수 환경 변수
```bash
# AWS 설정
AWS_REGION=ap-northeast-2
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key

# DynamoDB 설정
DYNAMODB_ENDPOINT=http://localhost:8001  # 로컬 개발용
DYNAMODB_TABLE_PREFIX=planit-dev

# gRPC 설정
GRPC_SERVER_PORT=9094
GRPC_INSIGHTAI_HOST=localhost
GRPC_INSIGHTAI_PORT=9095

# Bedrock 설정
BEDROCK_MODEL_ID=global.anthropic.claude-sonnet-4-5-20250929-v1:0
```

자세한 내용은 [ENVIRONMENT_VARIABLES.md](./ENVIRONMENT_VARIABLES.md) 참고

---

## 실행 방법

### 로컬 개발 환경
```bash
# 1. DynamoDB 로컬 실행 (포트 8001)
docker run -p 8001:8000 amazon/dynamodb-local

# 2. InsightAI-svc 실행 (Python gRPC 서버)
cd PlanIt-InsightAI-svc
python -m app.main_grpc

# 3. Insight-svc 실행 (Java BFF)
cd PlanIt-Insight-svc
./gradlew bootRun
```

### 서비스 확인
- Swagger UI: http://localhost:8084/swagger-ui.html
- Health Check: http://localhost:8084/api/v1/base/test

---

## 관련 문서

- [CHATBOT_BFF_MIGRATION.md](./CHATBOT_BFF_MIGRATION.md) - 챗봇 BFF 패턴 전환 가이드
- [GRPC_ACTION_LOG_SERVER_GUIDE.md](./GRPC_ACTION_LOG_SERVER_GUIDE.md) - ActionLog gRPC 서버 가이드
- [GRPC_REPORT_INTEGRATION_GUIDE.md](./GRPC_REPORT_INTEGRATION_GUIDE.md) - Report gRPC 통합 가이드
- [GROWTH_CALCULATION_FIX.md](./GROWTH_CALCULATION_FIX.md) - Growth 계산 로직 개선
- [ENVIRONMENT_VARIABLES.md](./ENVIRONMENT_VARIABLES.md) - 환경 변수 설정 가이드

---

## 버전 정보

- API Version: v1
- Service Version: 1.0.0
- Last Updated: 2026-03-08
