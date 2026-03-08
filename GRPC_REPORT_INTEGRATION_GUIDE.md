# gRPC Report Service 통합 가이드

## 개요
Insight-svc(Java)와 InsightAI-svc(Python) 간 통신을 REST API에서 gRPC로 전환

## 아키텍처

```
Insight-svc (Java)                    InsightAI-svc (Python)
Port: 8084                             Port: 50052 (gRPC)
┌─────────────────────┐               ┌──────────────────────┐
│ ReportGrpcClient    │──gRPC────────>│ ReportServiceServicer│
│ (@GrpcClient)       │               │ (Async)              │
└─────────────────────┘               └──────────────────────┘
         │                                      │
         │                                      │
         v                                      v
┌─────────────────────┐               ┌──────────────────────┐
│ AnalyticsService    │               │ ReportGeneratorService│
│ (통계 계산)          │               │ (Bedrock AI 호출)     │
└─────────────────────┘               └──────────────────────┘
         │                                      │
         v                                      v
┌─────────────────────┐               ┌──────────────────────┐
│ DynamoDB            │               │ AWS Bedrock          │
│ (리포트 저장)        │               │ (Claude 4.5 Sonnet)  │
└─────────────────────┘               └──────────────────────┘
```

## Step 1: Proto 파일 정의

### Java (Insight-svc)
**위치**: `PlanIt-Insight-svc/src/main/proto/report_service.proto`

```protobuf
syntax = "proto3";

option java_multiple_files = true;
option java_package = "com.planit.grpc.report";

package report;

service ReportService {
  rpc GenerateReport(GenerateReportRequest) returns (GenerateReportResponse);
}

message GenerateReportRequest {
  string user_id = 1;
  string report_type = 2;
  string target_period = 3;
  string statistics_data = 4;  // JSON 문자열
}

message GenerateReportResponse {
  bool success = 1;
  string report_data = 2;      // JSON 문자열
  string error_message = 3;
  string generated_at = 4;
}
```

### Python (InsightAI-svc)
**위치**: `PlanIt-InsightAI-svc/proto/report_service.proto`

동일한 내용 (java_* 옵션 제외)

## Step 2: Python gRPC 서버 구현

### 2.1 Proto 컴파일

```powershell
cd PlanIt-InsightAI-svc
./compile_report_proto.ps1
```

생성 파일:
- `proto/report_service_pb2.py`
- `proto/report_service_pb2_grpc.py`

### 2.2 Servicer 구현

**파일**: `app/grpc_server/report_servicer.py`

```python
class ReportServiceServicer(report_service_pb2_grpc.ReportServiceServicer):
    async def GenerateReport(self, request, context):
        # JSON 파싱
        statistics_data = json.loads(request.statistics_data)
        
        # 리포트 타입별 처리
        if request.report_type == "GROWTH":
            feedback = await self.report_service._generate_growth_feedback(...)
        
        # JSON 응답
        return report_service_pb2.GenerateReportResponse(
            success=True,
            report_data=json.dumps(report_data),
            generated_at=datetime.now().isoformat()
        )
```

### 2.3 gRPC 서버 실행

**파일**: `app/main_grpc_report.py`

```python
async def serve():
    server = grpc.aio.server(...)
    report_service_pb2_grpc.add_ReportServiceServicer_to_server(
        ReportServiceServicer(), server
    )
    server.add_insecure_port('[::]50052')
    await server.start()
```

**실행**:
```powershell
cd PlanIt-InsightAI-svc
python -m app.main_grpc_report
```

## Step 3: Java gRPC 클라이언트 구현

### 3.1 Proto 컴파일

```powershell
cd PlanIt-Insight-svc
./gradlew generateProto
```

생성 위치: `build/generated/source/proto/main/`

### 3.2 gRPC 클라이언트 구현

**파일**: `src/main/java/com/planit/analytics/grpc/ReportGrpcClient.java`

```java
@Component
public class ReportGrpcClient implements AIReportPort {
    
    @GrpcClient("report-service")
    private ReportServiceGrpc.ReportServiceBlockingStub reportServiceStub;
    
    @Override
    public AIReportResponse generateReport(AIReportRequest request) {
        try {
            // JSON 변환
            String statisticsDataJson = objectMapper.writeValueAsString(
                request.getStatisticsData()
            );
            
            // gRPC 호출
            GenerateReportResponse grpcResponse = reportServiceStub
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .generateReport(
                    GenerateReportRequest.newBuilder()
                        .setUserId(request.getUserId())
                        .setReportType(request.getReportType())
                        .setTargetPeriod(request.getTargetPeriod())
                        .setStatisticsData(statisticsDataJson)
                        .build()
                );
            
            // 응답 파싱
            Map<String, Object> reportData = objectMapper.readValue(
                grpcResponse.getReportData(), HashMap.class
            );
            
            return AIReportResponse.builder()
                .success(grpcResponse.getSuccess())
                .reportData(reportData)
                .build();
                
        } catch (StatusRuntimeException e) {
            // gRPC 통신 에러 (장애 격리)
            return createFallbackResponse("통신 실패: " + e.getMessage());
        }
    }
}
```

### 3.3 application.yml 설정

```yaml
grpc:
  client:
    report-service:
      address: 'static://localhost:50052'
      negotiationType: plaintext
      enableKeepAlive: true
      keepAliveTime: 30s
      keepAliveTimeout: 10s
```

### 3.4 기존 REST Adapter 제거

```powershell
# RestApiAdapter.java 삭제
rm src/main/java/com/planit/analytics/adapter/RestApiAdapter.java
```

## 장애 격리 (Fault Isolation)

### Python 서버 다운 시나리오

```java
try {
    GenerateReportResponse response = reportServiceStub
        .withDeadlineAfter(30, TimeUnit.SECONDS)
        .generateReport(request);
} catch (StatusRuntimeException e) {
    // Python 서버가 죽어있어도 Java 서버는 정상 동작
    log.error("gRPC call failed: {}", e.getStatus());
    return createFallbackResponse("AI 서비스 일시 중단");
}
```

### Fallback 응답

```java
private AIReportResponse createFallbackResponse(String errorMessage) {
    return AIReportResponse.builder()
        .success(false)
        .reportData(new HashMap<>())
        .errorMessage(errorMessage)
        .build();
}
```

## 테스트 방법

### 1. Python gRPC 서버 시작

```powershell
cd PlanIt-InsightAI-svc
python -m app.main_grpc_report
```

로그 확인:
```
INFO - Starting Report gRPC server on [::]:50052
INFO - Report gRPC server started successfully
```

### 2. Java 서버 시작

```powershell
cd PlanIt-Insight-svc
./gradlew bootRun
```

로그 확인:
```
INFO - gRPC client 'report-service' connected to localhost:50052
```

### 3. 배치 API 테스트

```powershell
Invoke-WebRequest -Uri "http://localhost:8084/api/v1/batch/generate-report?userId=test-user-001&yearMonth=2026-02" -Method POST
```

### 4. gRPC 직접 테스트 (grpcurl)

```powershell
# 서비스 목록 확인
grpcurl -plaintext localhost:50052 list

# GenerateReport 호출
grpcurl -plaintext -d '{
  "user_id": "test-user-001",
  "report_type": "GROWTH",
  "target_period": "2026-02",
  "statistics_data": "{\"topicName\":\"운동\",\"growthRate\":25,\"previousRate\":60,\"currentRate\":75}"
}' localhost:50052 report.ReportService/GenerateReport
```

## 포트 정리

| 서비스 | 프로토콜 | 포트 | 용도 |
|--------|---------|------|------|
| Insight-svc | HTTP | 8084 | REST API |
| Insight-svc | gRPC | 9090 | ActionLog 서버 (Schedule 수신) |
| InsightAI-svc | HTTP | 8085 | FastAPI (Deprecated) |
| InsightAI-svc | gRPC | 50051 | Chatbot 서비스 |
| InsightAI-svc | gRPC | 50052 | Report 서비스 (신규) |

## 마이그레이션 체크리스트

- [x] Proto 파일 정의 (Java/Python)
- [x] Python gRPC 서버 구현
- [x] Java gRPC 클라이언트 구현
- [x] application.yml 설정 추가
- [x] 장애 격리 로직 추가
- [ ] Proto 컴파일 (Python)
- [ ] Proto 컴파일 (Java)
- [ ] Python gRPC 서버 실행
- [ ] Java 서버 재시작
- [ ] 통합 테스트
- [ ] REST API 제거 (RestApiAdapter.java)
- [ ] FastAPI 엔드포인트 제거 (app/api/reports.py)

## 트러블슈팅

### Python: ModuleNotFoundError: No module named 'proto'

```powershell
cd PlanIt-InsightAI-svc
./compile_report_proto.ps1
```

### Java: cannot find symbol: class GenerateReportRequest

```powershell
cd PlanIt-Insight-svc
./gradlew clean generateProto build
```

### gRPC: UNAVAILABLE: io exception

Python gRPC 서버가 실행 중인지 확인:
```powershell
netstat -ano | findstr :50052
```

### Timeout: DEADLINE_EXCEEDED

`withDeadlineAfter(30, TimeUnit.SECONDS)` 시간 증가

## 참고 자료

- [gRPC Java Documentation](https://grpc.io/docs/languages/java/)
- [gRPC Python Documentation](https://grpc.io/docs/languages/python/)
- [net.devh gRPC Spring Boot Starter](https://github.com/grpc-ecosystem/grpc-spring)
- [Schedule ↔ Insight gRPC 통합 가이드](./GRPC_ACTION_LOG_SERVER_GUIDE.md)
