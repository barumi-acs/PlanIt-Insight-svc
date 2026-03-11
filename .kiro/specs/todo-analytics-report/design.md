# Design Document

## Overview

본 문서는 PlanIt Todo Analytics & Report 서비스의 시스템 설계를 정의합니다. 본 시스템은 Polyglot MSA 아키텍처를 채택하여 트랜잭션 안정성이 중요한 코어 비즈니스(Java)와 최신 AI 기능(Python)을 분리합니다.

### System Goals

- 사용자의 할 일 처리 데이터를 분석하여 개인화된 인사이트 제공
- AWS Bedrock 기반 AI를 활용한 자연스러운 피드백 생성
- 확장 가능한 MSA 아키텍처로 Phase 1(동기) → Phase 2(비동기) 전환 지원
- Hexagonal Architecture를 통한 비즈니스 로직과 인프라 계층 분리

### Technology Stack

**Service A: Insight Core Service (Java)**
- Language: Java 17
- Framework: Spring Boot 3.5.11
- Database: MariaDB (Action Logs), DynamoDB (AI Reports)
- Libraries: Spring Data JPA, Lombok, ShedLock, CompletableFuture

**Service B: AI Agent Service (Python)**
- Language: Python 3.11
- Framework: FastAPI
- AI Infrastructure: AWS Bedrock (Claude 3.5 Sonnet v2)
- Libraries: boto3 / langchain-aws, pydantic

## Architecture

### System Architecture Diagram

```
┌─────────────────┐
│  Task Service   │
│    (External)   │
└────────┬────────┘
         │ Phase 1: REST API
         │ Phase 2: SQS/Kafka
         ▼
┌─────────────────────────────────────────────────────────┐
│           Service A: Insight Core (Java)                │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Controller Layer (REST API + Swagger)           │  │
│  └──────────────────┬───────────────────────────────┘  │
│                     ▼                                   │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Service Layer (Business Logic)                  │  │
│  │  - FeedbackService                               │  │
│  │  - AnalyticsService                              │  │
│  │  - BatchScheduler (@Scheduled + ShedLock)        │  │
│  └──────────┬────────────────────┬──────────────────┘  │
│             │                    │                      │
│             ▼                    ▼                      │
│  ┌──────────────────┐  ┌──────────────────────────┐   │
│  │  Port Interface  │  │  Repository Layer        │   │
│  │  - ActionLogPort │  │  - ActionLogRepository   │   │
│  │  - AIReportPort  │  │  - DynamoDBRepository    │   │
│  └────────┬─────────┘  └──────────────────────────┘   │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Adapter Layer (Infrastructure)                  │  │
│  │  - RestApiAdapter (Phase 1)                      │  │
│  │  - SQSAdapter (Phase 2 - Future)                 │  │
│  └──────────────────┬───────────────────────────────┘  │
└────────────────────┼────────────────────────────────────┘
                     │ HTTP/REST
                     ▼
┌─────────────────────────────────────────────────────────┐
│           Service B: AI Agent (Python)                  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  FastAPI Endpoints                               │  │
│  │  - POST /ai/reports/generate                     │  │
│  │  - POST /ai/chat/query                           │  │
│  └──────────────────┬───────────────────────────────┘  │
│                     ▼                                   │
│  ┌──────────────────────────────────────────────────┐  │
│  │  AI Service Layer                                │  │
│  │  - ReportGeneratorService (Context Injection)    │  │
│  │  - ChatbotService (MCP + Tool Use)               │  │
│  └──────────────────┬───────────────────────────────┘  │
│                     ▼                                   │
│  ┌──────────────────────────────────────────────────┐  │
│  │  AWS Bedrock Client                              │  │
│  │  - Converse API                                  │  │
│  │  - Tool Use (Function Calling)                   │  │
│  │  - Prompt Chaining                               │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```


### Hexagonal Architecture (Port & Adapter Pattern)

Service A는 향후 통신 방식 변경(REST → SQS/Kafka)에 대비하여 Hexagonal Architecture를 적용합니다.

**Port (Interface)**
- 비즈니스 로직이 의존하는 추상화된 인터페이스
- 통신 방식에 독립적인 메서드 시그니처 정의

**Adapter (Implementation)**
- Port 인터페이스의 구체적인 구현체
- Phase 1: RestApiAdapter (동기 REST 호출)
- Phase 2: SQSAdapter (비동기 메시지 발행)

**장점**
- 비즈니스 로직 변경 없이 인프라 계층만 교체 가능
- 테스트 용이성 (Mock Adapter 사용)
- 명확한 책임 분리

### Phase 1 vs Phase 2 Communication

**Phase 1 (현재 구현)**
```
Task Service → REST API → Service A (ActionLogPort → RestApiAdapter)
```
- 동기식 HTTP 통신
- 즉시 응답 필요
- 간단한 구조

**Phase 2 (미래 확장)**
```
Task Service → SQS/Kafka → Service A (ActionLogPort → SQSAdapter → Consumer)
```
- 비동기 메시지 큐
- 느슨한 결합
- 높은 확장성

## Components and Interfaces

### Service A: Insight Core Service (Java)

#### Controller Layer

**FeedbackController**
```java
@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {
    
    private final FeedbackService feedbackService;
    
    @Operation(summary = "일간 응원 피드백 조회")
    @GetMapping("/daily-cheer")
    public ApiResponse<DailyCheerResponse> getDailyCheer(
        @RequestHeader("Authorization") String token
    );
    
    @Operation(summary = "AI 피드백 대시보드 조회")
    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(
        @RequestParam String yearMonth,
        @RequestParam Integer week,
        @RequestHeader("Authorization") String token
    );
}
```

**InternalActionLogController**
```java
@RestController
@RequestMapping("/internal/api/v1/action-logs")
@RequiredArgsConstructor
@Slf4j
public class InternalActionLogController {
    
    private final ActionLogService actionLogService;
    
    @PostMapping
    public ApiResponse<Void> receiveActionLog(
        @RequestBody @Valid ActionLogRequest request
    );
}
```


#### Service Layer

**FeedbackService**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {
    
    private final DynamoDBRepository dynamoDBRepository;
    private final ActionLogRepository actionLogRepository;
    
    public DailyCheerResponse getDailyCheer(String userId);
    public DashboardResponse getDashboard(String userId, String yearMonth, Integer week);
}
```

**AnalyticsService**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {
    
    private final ActionLogRepository actionLogRepository;
    
    @Async
    public CompletableFuture<GrowthFeedback> calculateGrowthRate(String userId);
    
    @Async
    public CompletableFuture<TimelineFeedback> calculateTimeline(String userId);
    
    @Async
    public CompletableFuture<PatternFeedback> analyzePostponePattern(String userId);
    
    @Async
    public CompletableFuture<SummaryFeedback> generateSummary(String userId);
}
```

**BatchScheduler**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportGenerationScheduler {
    
    private final AnalyticsService analyticsService;
    private final AIReportPort aiReportPort;
    private final DynamoDBRepository dynamoDBRepository;
    
    @Scheduled(cron = "0 0 2 * * MON") // 매주 월요일 새벽 2시
    @SchedulerLock(name = "weeklyReportGeneration", 
                   lockAtMostFor = "1h", 
                   lockAtLeastFor = "10m")
    public void generateWeeklyReports();
    
    @Scheduled(cron = "0 0 3 1 * *") // 매월 1일 새벽 3시
    @SchedulerLock(name = "monthlyReportGeneration", 
                   lockAtMostFor = "2h", 
                   lockAtLeastFor = "30m")
    public void generateMonthlyReports();
}
```

#### Port Interfaces

**ActionLogPort**
```java
public interface ActionLogPort {
    void sendActionLog(ActionLogDto actionLog);
}
```

**AIReportPort**
```java
public interface AIReportPort {
    AIReportResponse generateReport(AIReportRequest request);
}
```

#### Adapter Layer

**RestApiAdapter (Phase 1)**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RestApiAdapter implements ActionLogPort, AIReportPort {
    
    private final RestTemplate restTemplate;
    
    @Value("${service-b.base-url}")
    private String serviceBBaseUrl;
    
    @Override
    public void sendActionLog(ActionLogDto actionLog) {
        // Phase 1: 동기 REST 호출 (현재는 사용하지 않음)
    }
    
    @Override
    public AIReportResponse generateReport(AIReportRequest request) {
        String url = serviceBBaseUrl + "/ai/reports/generate";
        return restTemplate.postForObject(url, request, AIReportResponse.class);
    }
}
```

**SQSAdapter (Phase 2 - Future)**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SQSAdapter implements ActionLogPort {
    
    private final AmazonSQS amazonSQS;
    
    @Value("${aws.sqs.action-log-queue-url}")
    private String queueUrl;
    
    @Override
    public void sendActionLog(ActionLogDto actionLog) {
        // Phase 2: 비동기 SQS 메시지 발행
        SendMessageRequest sendMessageRequest = new SendMessageRequest()
            .withQueueUrl(queueUrl)
            .withMessageBody(toJson(actionLog));
        amazonSQS.sendMessage(sendMessageRequest);
    }
}
```


#### Repository Layer

**ActionLogRepository**
```java
@Repository
public interface ActionLogRepository extends JpaRepository<ActionLogEntity, Long> {
    
    @Query("SELECT a FROM ActionLogEntity a " +
           "WHERE a.userId = :userId " +
           "AND a.actionTime BETWEEN :startDate AND :endDate " +
           "AND a.deletedAt IS NULL")
    List<ActionLogEntity> findByUserIdAndDateRange(
        @Param("userId") String userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT a.dayOfWeek, COUNT(a) as count, " +
           "SUM(CASE WHEN a.actionType = 'COMPLETED' THEN 1 ELSE 0 END) as completed " +
           "FROM ActionLogEntity a " +
           "WHERE a.userId = :userId " +
           "AND a.actionTime >= :since " +
           "AND a.deletedAt IS NULL " +
           "GROUP BY a.dayOfWeek")
    List<DayOfWeekStats> calculateDayOfWeekStats(
        @Param("userId") String userId,
        @Param("since") LocalDateTime since
    );
}
```

**DynamoDBRepository**
```java
@Repository
@RequiredArgsConstructor
@Slf4j
public class DynamoDBRepository {
    
    private final DynamoDbClient dynamoDbClient;
    
    @Value("${aws.dynamodb.table-name}")
    private String tableName;
    
    public void saveReport(String userId, String reportType, String yearMonth, 
                          Map<String, Object> reportData);
    
    public Optional<Map<String, Object>> getReport(String userId, String reportType, 
                                                   String yearMonth);
}
```

### Service B: AI Agent Service (Python)

#### FastAPI Endpoints

**main.py**
```python
from fastapi import FastAPI, HTTPException, Depends
from pydantic import BaseModel
from typing import Dict, Any
import logging

app = FastAPI(title="AI Agent Service")

@app.post("/ai/reports/generate")
async def generate_report(request: ReportGenerationRequest) -> ReportGenerationResponse:
    """
    Context Injection 방식으로 AI 리포트 생성
    Service A가 조회한 통계 데이터를 받아 Bedrock으로 피드백 생성
    """
    pass

@app.post("/ai/chat/query")
async def process_chat_query(request: ChatQueryRequest) -> ChatQueryResponse:
    """
    MCP 방식으로 사용자 질의 처리
    Claude가 Tool Use를 통해 자율적으로 데이터 조회 및 분석
    """
    pass
```


#### AI Service Layer

**report_generator_service.py**
```python
from typing import Dict, Any, List
import boto3
from botocore.config import Config

class ReportGeneratorService:
    """
    Context Injection 방식의 AI 리포트 생성 서비스
    Service A가 제공한 통계 데이터를 Bedrock에 전달하여 피드백 생성
    """
    
    def __init__(self):
        self.bedrock_client = boto3.client(
            'bedrock-runtime',
            config=Config(region_name='us-east-1')
        )
        self.model_id = "anthropic.claude-3-5-sonnet-20241022-v2:0"
    
    async def generate_report(self, stats_data: Dict[str, Any]) -> Dict[str, Any]:
        """
        Prompt Chaining 기법으로 4가지 피드백 생성
        1. Growth Feedback
        2. Timeline Feedback
        3. Pattern Feedback
        4. Summary Feedback
        """
        
        # Step 1: Growth Feedback 생성
        growth_feedback = await self._generate_growth_feedback(stats_data['growth'])
        
        # Step 2: Timeline Feedback 생성
        timeline_feedback = await self._generate_timeline_feedback(stats_data['timeline'])
        
        # Step 3: Pattern Feedback 생성
        pattern_feedback = await self._generate_pattern_feedback(stats_data['pattern'])
        
        # Step 4: Summary Feedback 생성 (이전 3개 결과 참조)
        summary_feedback = await self._generate_summary_feedback(
            growth_feedback, timeline_feedback, pattern_feedback
        )
        
        return {
            'growth': growth_feedback,
            'timeline': timeline_feedback,
            'pattern': pattern_feedback,
            'summary': summary_feedback
        }
    
    async def _generate_growth_feedback(self, growth_data: Dict) -> Dict:
        """Bedrock Converse API 호출"""
        prompt = f"""
        사용자의 성장 데이터를 분석하여 격려 메시지를 생성해주세요.
        
        데이터:
        - 주제: {growth_data['topic_name']}
        - 성장률: {growth_data['growth_rate']}%
        
        다음 JSON 형식으로 응답해주세요:
        {{
            "topicName": "주제명",
            "growthRate": 숫자,
            "message": "격려 메시지"
        }}
        """
        
        response = self.bedrock_client.converse(
            modelId=self.model_id,
            messages=[{"role": "user", "content": [{"text": prompt}]}],
            inferenceConfig={"maxTokens": 500, "temperature": 0.7}
        )
        
        return self._parse_json_response(response)
```


**chatbot_service.py**
```python
from typing import Dict, Any, List
import boto3
import json

class ChatbotService:
    """
    MCP (Model Context Protocol) 방식의 챗봇 서비스
    Claude가 Tool Use를 통해 자율적으로 데이터 조회
    """
    
    def __init__(self):
        self.bedrock_client = boto3.client('bedrock-runtime')
        self.model_id = "anthropic.claude-3-5-sonnet-20241022-v2:0"
        self.tools = self._define_tools()
    
    def _define_tools(self) -> List[Dict]:
        """Bedrock Tool Use를 위한 도구 정의"""
        return [
            {
                "toolSpec": {
                    "name": "query_user_action_logs",
                    "description": "사용자의 할 일 처리 로그를 조회합니다",
                    "inputSchema": {
                        "json": {
                            "type": "object",
                            "properties": {
                                "user_id": {"type": "string"},
                                "start_date": {"type": "string"},
                                "end_date": {"type": "string"},
                                "action_type": {"type": "string", "enum": ["COMPLETED", "POSTPONED"]}
                            },
                            "required": ["user_id", "start_date", "end_date"]
                        }
                    }
                }
            },
            {
                "toolSpec": {
                    "name": "calculate_completion_rate",
                    "description": "특정 기간의 완료율을 계산합니다",
                    "inputSchema": {
                        "json": {
                            "type": "object",
                            "properties": {
                                "user_id": {"type": "string"},
                                "period": {"type": "string", "enum": ["week", "month"]}
                            },
                            "required": ["user_id", "period"]
                        }
                    }
                }
            }
        ]
    
    async def process_query(self, user_id: str, query: str) -> str:
        """
        사용자 질의를 처리하고 Claude가 자율적으로 도구를 사용하여 답변 생성
        """
        messages = [{"role": "user", "content": [{"text": query}]}]
        
        # Tool Use 활성화하여 Bedrock 호출
        response = self.bedrock_client.converse(
            modelId=self.model_id,
            messages=messages,
            toolConfig={"tools": self.tools}
        )
        
        # Claude가 도구 사용을 요청하면 실행
        if self._has_tool_use(response):
            tool_results = await self._execute_tools(response, user_id)
            
            # 도구 실행 결과를 Claude에 전달하여 최종 답변 생성
            messages.append({"role": "assistant", "content": response['output']['message']['content']})
            messages.append({"role": "user", "content": tool_results})
            
            final_response = self.bedrock_client.converse(
                modelId=self.model_id,
                messages=messages
            )
            
            return self._extract_text(final_response)
        
        return self._extract_text(response)
    
    async def _execute_tools(self, response: Dict, user_id: str) -> List[Dict]:
        """Claude가 요청한 도구를 실제로 실행"""
        tool_results = []
        
        for content in response['output']['message']['content']:
            if 'toolUse' in content:
                tool_use = content['toolUse']
                tool_name = tool_use['name']
                tool_input = tool_use['input']
                
                # 도구 실행
                if tool_name == "query_user_action_logs":
                    result = await self._query_database(tool_input)
                elif tool_name == "calculate_completion_rate":
                    result = await self._calculate_rate(tool_input)
                
                tool_results.append({
                    "toolResult": {
                        "toolUseId": tool_use['toolUseId'],
                        "content": [{"json": result}]
                    }
                })
        
        return tool_results
```


## Data Models

### Service A: MariaDB Entities

**ActionLogEntity.java**
```java
@Entity
@Table(name = "user_action_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE user_action_logs SET deleted_at = CURRENT_TIMESTAMP(6) WHERE log_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ActionLogEntity extends BaseTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;
    
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    
    @Column(name = "task_id", nullable = false)
    private Long taskId;
    
    @Column(name = "goals_id")
    private Long goalsId;
    
    @Column(name = "action_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ActionType actionType;
    
    @Column(name = "action_time", nullable = false)
    private LocalDateTime actionTime;
    
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    
    @Column(name = "postponed_to_date")
    private LocalDate postponedToDate;
    
    @Column(name = "day_of_week", nullable = false, length = 10)
    private String dayOfWeek;
    
    @Column(name = "hour_of_day", nullable = false)
    private Integer hourOfDay;
    
    @PrePersist
    public void prePersist() {
        if (actionTime != null) {
            this.dayOfWeek = actionTime.getDayOfWeek().toString();
            this.hourOfDay = actionTime.getHour();
        }
    }
}

public enum ActionType {
    COMPLETED,
    POSTPONED
}
```

### Service A: DynamoDB Models

**AIReportDocument**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIReportDocument {
    
    private String pk;  // "USER#{userId}"
    private String sk;  // "REPORT#{yearMonth}#{reportType}"
    private String createdAt;
    private Map<String, Object> reportData;
    
    public static String generatePK(String userId) {
        return "USER#" + userId;
    }
    
    public static String generateSK(String yearMonth, String reportType) {
        return "REPORT#" + yearMonth + "#" + reportType;
    }
}
```

### Service B: Pydantic Models

**models.py**
```python
from pydantic import BaseModel, Field
from typing import Dict, Any, List, Optional
from datetime import datetime

class ReportGenerationRequest(BaseModel):
    user_id: str
    year_month: str
    stats_data: Dict[str, Any] = Field(
        description="Service A가 조회한 통계 데이터"
    )

class ReportGenerationResponse(BaseModel):
    success: bool
    report_data: Dict[str, Any]
    generated_at: datetime

class ChatQueryRequest(BaseModel):
    user_id: str
    query: str
    context: Optional[Dict[str, Any]] = None

class ChatQueryResponse(BaseModel):
    answer: str
    sources: List[str] = Field(default_factory=list)
    generated_at: datetime
```


## AI Workflow Sequences

### Workflow 1: 배치 리포트 생성 (Context Injection)

```
1. Service A: @Scheduled 배치 실행 (ShedLock으로 중복 방지)
   └─> 대상 사용자 목록 조회 (일 평균 Task 3개 이상)

2. Service A: 각 사용자별 통계 데이터 병렬 조회 (CompletableFuture)
   ├─> CompletableFuture<GrowthFeedback> calculateGrowthRate()
   ├─> CompletableFuture<TimelineFeedback> calculateTimeline()
   ├─> CompletableFuture<PatternFeedback> analyzePostponePattern()
   └─> CompletableFuture<SummaryFeedback> generateSummary()
   
3. Service A: 모든 CompletableFuture 완료 대기
   └─> CompletableFuture.allOf(...).join()

4. Service A → Service B: POST /ai/reports/generate
   Request Body:
   {
     "user_id": "USER123",
     "year_month": "2026-02",
     "stats_data": {
       "growth": { "topic_name": "운동", "growth_rate": 24 },
       "timeline": { "chart_data": [...] },
       "pattern": { "worst_day": "SUNDAY", "avg_postpone_count": 5 },
       "summary": { "achievement_trend": "+12%" }
     }
   }

5. Service B: Prompt Chaining으로 AI 피드백 생성
   ├─> Step 1: Growth Feedback 생성 (Bedrock Converse API)
   ├─> Step 2: Timeline Feedback 생성
   ├─> Step 3: Pattern Feedback 생성
   └─> Step 4: Summary Feedback 생성 (이전 결과 참조)

6. Service B → Service A: 규격화된 JSON 응답
   Response Body:
   {
     "success": true,
     "report_data": {
       "growth": { "topicName": "운동", "growthRate": 24, "message": "..." },
       "timeline": { "chartData": [...] },
       "pattern": { "worstDay": "SUNDAY", "message": "..." },
       "summary": { "message": "..." }
     }
   }

7. Service A: DynamoDB에 AI Report 저장
   └─> PK: "USER#USER123"
   └─> SK: "REPORT#2026-02#DASHBOARD"
   └─> report_data: { ... }

8. Service A: 배치 완료 로그 기록
```

### Workflow 2: MCP 챗봇 질의 처리 (Tool Use)

```
1. User → Frontend → Service A: 자연어 질의
   "지난 주에 내가 가장 많이 미룬 요일은 언제야?"

2. Service A → Service B: POST /ai/chat/query
   Request Body:
   {
     "user_id": "USER123",
     "query": "지난 주에 내가 가장 많이 미룬 요일은 언제야?"
   }

3. Service B: Bedrock Converse API 호출 (Tool Use 활성화)
   └─> Claude가 질의를 분석하고 필요한 도구 결정

4. Claude: "query_user_action_logs" 도구 사용 요청
   Tool Input:
   {
     "user_id": "USER123",
     "start_date": "2026-02-17",
     "end_date": "2026-02-23",
     "action_type": "POSTPONED"
   }

5. Service B: 도구 실행 (MariaDB 조회)
   └─> SELECT day_of_week, COUNT(*) 
       FROM user_action_logs 
       WHERE user_id = 'USER123' 
       AND action_type = 'POSTPONED'
       AND action_time BETWEEN '2026-02-17' AND '2026-02-23'
       GROUP BY day_of_week
       ORDER BY COUNT(*) DESC

6. Service B: 도구 실행 결과를 Claude에 전달
   Tool Result:
   [
     {"day_of_week": "SUNDAY", "count": 8},
     {"day_of_week": "SATURDAY", "count": 5},
     ...
   ]

7. Claude: 최종 답변 생성
   "지난 주에 가장 많이 미룬 요일은 일요일이에요. 
    총 8개의 할 일을 미루셨네요. 
    주말에는 계획을 조금 줄여보는 것은 어떨까요?"

8. Service B → Service A → Frontend → User: 답변 전달
```


### Context Injection vs MCP 비교

| 구분 | Context Injection (배치) | MCP (챗봇) |
|------|-------------------------|-----------|
| 데이터 조회 주체 | Service A (Java) | Service B (Python + Claude) |
| AI 역할 | 텍스트 생성만 담당 | 데이터 조회 + 분석 + 답변 생성 |
| 데이터 전달 방식 | Service A가 미리 조회하여 전달 | Claude가 필요시 Tool Use로 조회 |
| 응답 형식 | 규격화된 JSON | 자연어 답변 |
| 사용 시나리오 | 정기 리포트 생성 | 사용자 질의 응답 |
| 장점 | 예측 가능한 성능, 캐싱 가능 | 유연한 질의 처리, 심층 분석 |
| 단점 | 고정된 분석 항목 | 응답 시간 가변적 |

## Error Handling

### Service A: Error Codes

```java
public enum ErrorCode {
    // Common Errors (C)
    C4001("C4001", "필수 파라미터가 누락되었습니다"),
    C4011("C4011", "JWT 토큰이 만료되었습니다"),
    C5001("C5001", "서버 내부 오류가 발생했습니다"),
    
    // Insight Service Errors (IS)
    IS4041("IS4041", "분석할 통계 데이터가 부족합니다"),
    IS5001("IS5001", "데이터 저장 중 오류가 발생했습니다"),
    
    // AI Service Errors (AI)
    AI5001("AI5001", "AI 서비스 호출 중 타임아웃이 발생했습니다"),
    AI4001("AI4001", "잘못된 AI 요청 형식입니다");
    
    private final String code;
    private final String message;
}
```

### Service A: Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        log.error("Custom exception occurred: {}", e.getErrorCode(), e);
        return ResponseEntity
            .status(e.getErrorCode().getHttpStatus())
            .body(ApiResponse.error(e.getErrorCode()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception occurred", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ErrorCode.C5001));
    }
}
```

### Service B: Error Handling

```python
from fastapi import HTTPException
from enum import Enum

class AIErrorCode(str, Enum):
    AI5001 = "AI5001"  # Bedrock 타임아웃
    AI4001 = "AI4001"  # 잘못된 요청
    AI5002 = "AI5002"  # Bedrock 호출 실패

@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "code": "AI5001",
            "message": "AI 서비스 내부 오류가 발생했습니다",
            "timestamp": datetime.now().isoformat()
        }
    )
```


## Configuration Management

### Service A: application.yml

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: insight-core-service
  
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: ${SPRING_DATASOURCE_URL:jdbc:mariadb://localhost:3306/planit_insight_db}
    username: ${SPRING_DATASOURCE_USERNAME:root}
    password: ${SPRING_DATASOURCE_PASSWORD:root}
  
  jpa:
    hibernate:
      ddl-auto: create
    show-sql: ${SHOW_SQL:false}
  
  task:
    execution:
      pool:
        core-size: 5
        max-size: 10

# Service B 연동
service-b:
  base-url: ${SERVICE_B_BASE_URL:http://localhost:8000}
  timeout: ${SERVICE_B_TIMEOUT:10000}

# AWS DynamoDB
aws:
  dynamodb:
    table-name: ${DYNAMODB_TABLE_NAME:ai_reports}
    region: ${AWS_REGION:us-east-1}

# ShedLock
shedlock:
  defaults:
    lock-at-most-for: 1h
    lock-at-least-for: 10m

# Swagger
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

### Service B: .env

```env
# Server
PORT=8000
LOG_LEVEL=INFO

# AWS Bedrock
AWS_REGION=us-east-1
BEDROCK_MODEL_ID=anthropic.claude-3-5-sonnet-20241022-v2:0

# Database (MCP Tool Use용)
DB_HOST=localhost
DB_PORT=3306
DB_NAME=planit_insight_db
DB_USER=root
DB_PASSWORD=root

# Service A 연동
SERVICE_A_BASE_URL=http://localhost:8080
```

### Service B: config.py

```python
from pydantic_settings import BaseSettings
from functools import lru_cache

class Settings(BaseSettings):
    port: int = 8000
    log_level: str = "INFO"
    
    aws_region: str = "us-east-1"
    bedrock_model_id: str = "anthropic.claude-3-5-sonnet-20241022-v2:0"
    
    db_host: str = "localhost"
    db_port: int = 3306
    db_name: str = "planit_insight_db"
    db_user: str = "root"
    db_password: str = "root"
    
    service_a_base_url: str = "http://localhost:8080"
    
    class Config:
        env_file = ".env"

@lru_cache()
def get_settings() -> Settings:
    return Settings()
```


## Correctness Properties

A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.

### Property 1: Action Log 저장 완전성

*For any* Task 완료 또는 미루기 처리, 해당 처리에 대한 Action_Log가 RDB에 저장되어야 한다

**Validates: Requirements 1.1, 1.2**

### Property 2: Action Type 유효성

*For any* 저장된 Action_Log, action_type 필드는 'COMPLETED' 또는 'POSTPONED' 중 하나여야 한다

**Validates: Requirements 1.3**

### Property 3: 시간 정보 자동 계산

*For any* Action_Log, day_of_week와 hour_of_day는 action_time으로부터 자동으로 계산되어야 하며, 계산 결과가 action_time과 일치해야 한다

**Validates: Requirements 1.4, 18.4**

### Property 4: 미루기 날짜 기록

*For any* 미루기 처리된 Task, postponed_to_date 필드는 null이 아니어야 하며 due_date보다 미래 날짜여야 한다

**Validates: Requirements 1.5**

### Property 5: 요일별 완료율 계산 정확성

*For any* 사용자와 요일, 계산된 완료율은 (해당 요일의 완료 건수 / 해당 요일의 전체 Task 건수) * 100과 일치해야 한다

**Validates: Requirements 2.3**

### Property 6: 데이터 범위 제한

*For any* 요일별 완료율 계산, 최근 3개월 이내의 데이터만 사용되어야 한다

**Validates: Requirements 2.2**

### Property 7: API 응답 형식 일관성

*For any* 성공한 API 호출, 응답은 ApiResponse 공통 포맷(code, message, data, timestamp)을 따라야 한다

**Validates: Requirements 9.5, 10.5**

### Property 8: 일간 응원 피드백 필수 필드

*For any* 일간 응원 피드백 조회 응답, targetDate, dayOfWeek, cheerData 필드가 포함되어야 한다

**Validates: Requirements 9.2, 9.3**

### Property 9: 대시보드 피드백 완전성

*For any* 대시보드 조회 응답, growth, timeline, pattern, summary 4가지 피드백이 모두 포함되어야 한다

**Validates: Requirements 10.3**

### Property 10: NoSQL 단일 쿼리 조회

*For any* AI_Report 조회, PK와 SK를 사용한 단일 GetItem 쿼리로 조회되어야 한다

**Validates: Requirements 11.3**

### Property 11: Soft Delete 동작

*For any* 삭제된 데이터, deleted_at 필드는 null이 아니어야 하며, 일반 조회 시 결과에 포함되지 않아야 한다

**Validates: Requirements 14.3, 14.4**

### Property 12: ActionLogPort 호출 완전성

*For any* Task Service의 성공한 트랜잭션, ActionLogPort가 호출되어야 하며 user_id, task_id, action_type, action_time, due_date 필드를 포함해야 한다

**Validates: Requirements 17.1, 17.2**

### Property 13: 서비스 간 통신 실패 격리

*For any* Insight_Service 호출 실패, Task_Service의 사용자 요청은 성공으로 처리되어야 하며 에러 로그가 기록되어야 한다

**Validates: Requirements 17.4**

### Property 14: 타임아웃 처리

*For any* 3초를 초과하는 Insight_Service 호출, 타임아웃이 발생하고 에러 로그가 기록되어야 한다

**Validates: Requirements 17.5**

### Property 15: 입력 데이터 검증

*For any* 로그 전송 API 요청, 필수 필드(user_id, task_id, action_type, action_time)가 누락되거나 유효하지 않으면 검증 에러가 발생해야 한다

**Validates: Requirements 18.1**

### Property 16: 데이터 저장 후 조회 일관성

*For any* 성공적으로 저장된 Action_Log, 즉시 조회 시 저장한 데이터와 동일한 내용이 반환되어야 한다

**Validates: Requirements 18.3**


## Testing Strategy

### Dual Testing Approach

본 시스템은 Unit Testing과 Property-Based Testing을 병행하여 포괄적인 테스트 커버리지를 확보합니다.

**Unit Tests**
- 특정 예시와 edge case 검증
- 통합 지점 테스트
- 에러 조건 테스트
- 목적: 구체적인 버그 발견

**Property-Based Tests**
- 보편적 속성을 다양한 입력에 대해 검증
- 랜덤 데이터 생성을 통한 포괄적 커버리지
- 최소 100회 반복 실행
- 목적: 일반적 정확성 검증

### Service A: Java Testing

**Testing Framework**
- JUnit 5
- Mockito
- jqwik (Property-Based Testing)

**Test Structure**
```java
@ExtendWith(MockitoExtension.class)
class ActionLogServiceTest {
    
    @Mock
    private ActionLogRepository actionLogRepository;
    
    @InjectMocks
    private ActionLogService actionLogService;
    
    // Unit Test Example
    @Test
    @DisplayName("유효한 Action Log 저장 시 성공")
    void saveActionLog_WithValidData_Success() {
        // Given
        ActionLogRequest request = createValidRequest();
        
        // When
        actionLogService.saveActionLog(request);
        
        // Then
        verify(actionLogRepository).save(any(ActionLogEntity.class));
    }
    
    // Property-Based Test Example
    @Property(tries = 100)
    @DisplayName("Feature: todo-analytics-report, Property 3: 시간 정보 자동 계산")
    void actionLog_AutoCalculatesTimeFields(@ForAll LocalDateTime actionTime) {
        // Given
        ActionLogEntity entity = ActionLogEntity.builder()
            .actionTime(actionTime)
            .build();
        
        // When
        entity.prePersist();
        
        // Then
        assertThat(entity.getDayOfWeek())
            .isEqualTo(actionTime.getDayOfWeek().toString());
        assertThat(entity.getHourOfDay())
            .isEqualTo(actionTime.getHour());
    }
}
```

### Service B: Python Testing

**Testing Framework**
- pytest
- pytest-asyncio
- Hypothesis (Property-Based Testing)

**Test Structure**
```python
import pytest
from hypothesis import given, strategies as st
from datetime import datetime

class TestReportGeneratorService:
    
    # Unit Test Example
    @pytest.mark.asyncio
    async def test_generate_report_with_valid_data_success(self):
        """유효한 통계 데이터로 리포트 생성 시 성공"""
        # Given
        service = ReportGeneratorService()
        stats_data = create_valid_stats_data()
        
        # When
        result = await service.generate_report(stats_data)
        
        # Then
        assert result['success'] is True
        assert 'growth' in result['report_data']
        assert 'timeline' in result['report_data']
    
    # Property-Based Test Example
    @given(
        growth_rate=st.integers(min_value=-100, max_value=200),
        topic_name=st.text(min_size=1, max_size=50)
    )
    def test_growth_feedback_format(self, growth_rate, topic_name):
        """
        Feature: todo-analytics-report, Property 9: 대시보드 피드백 완전성
        For any growth data, the feedback must contain required fields
        """
        # Given
        growth_data = {'growth_rate': growth_rate, 'topic_name': topic_name}
        
        # When
        feedback = generate_growth_feedback(growth_data)
        
        # Then
        assert 'topicName' in feedback
        assert 'growthRate' in feedback
        assert 'message' in feedback
        assert isinstance(feedback['message'], str)
```

### Integration Testing

**Service A ↔ Service B Integration**
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "service-b.base-url=http://localhost:${wiremock.server.port}"
})
class ServiceIntegrationTest {
    
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();
    
    @Test
    void batchScheduler_CallsServiceB_AndSavesToDynamoDB() {
        // Given
        wireMock.stubFor(post("/ai/reports/generate")
            .willReturn(okJson("{\"success\": true, \"report_data\": {}}")));
        
        // When
        batchScheduler.generateWeeklyReports();
        
        // Then
        wireMock.verify(postRequestedFor(urlEqualTo("/ai/reports/generate")));
        // Verify DynamoDB save
    }
}
```

### Test Coverage Goals

- Unit Test Coverage: 80% 이상
- Property-Based Test: 모든 Correctness Properties 구현
- Integration Test: 주요 서비스 간 통신 시나리오 커버
- E2E Test: 주요 사용자 시나리오 (배치 리포트 생성, API 조회)


## Security Considerations

### Authentication & Authorization

**JWT Token Validation**
```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) {
        String token = extractToken(request);
        
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserId(token);
            // Set authentication context
        } else {
            throw new CustomException(ErrorCode.C4011);
        }
        
        filterChain.doFilter(request, response);
    }
}
```

### Data Privacy

- 사용자는 자신의 데이터만 조회 가능
- JWT에서 추출한 user_id로 데이터 필터링
- 다른 사용자의 리포트 조회 시도 시 접근 거부

### AWS Credentials Management

**Service A: IAM Role 기반 인증**
```java
@Configuration
public class DynamoDBConfig {
    
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
```

**Service B: Bedrock 인증**
```python
import boto3
from botocore.config import Config

def create_bedrock_client():
    """
    AWS Default Credential Provider Chain 사용
    1. 환경 변수 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
    2. ~/.aws/credentials 파일
    3. IAM Role (ECS/EC2)
    """
    return boto3.client(
        'bedrock-runtime',
        config=Config(region_name='us-east-1')
    )
```

### Sensitive Data Logging

```java
@Slf4j
public class LoggingUtils {
    
    public static String maskSensitiveData(String data) {
        if (data == null || data.length() < 4) {
            return "****";
        }
        return data.substring(0, 2) + "****" + data.substring(data.length() - 2);
    }
    
    public static void logApiCall(String endpoint, String userId) {
        log.info("API 호출: endpoint={}, userId={}", 
                endpoint, maskSensitiveData(userId));
    }
}
```

## Deployment Architecture

### Local Development

```
┌─────────────────┐     ┌─────────────────┐
│  Service A      │────▶│  Service B      │
│  localhost:8080 │     │  localhost:8000 │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│  MariaDB        │     │  AWS Bedrock    │
│  localhost:3306 │     │  (AWS Account)  │
└─────────────────┘     └─────────────────┘
         │
         ▼
┌─────────────────┐
│  DynamoDB Local │
│  localhost:8000 │
└─────────────────┘
```

### Production (AWS EKS)

```
┌──────────────────────────────────────────────┐
│              AWS EKS Cluster                 │
│                                              │
│  ┌────────────────┐    ┌────────────────┐  │
│  │  Service A Pod │───▶│  Service B Pod │  │
│  │  (Java)        │    │  (Python)      │  │
│  └────────┬───────┘    └────────┬───────┘  │
│           │                     │           │
└───────────┼─────────────────────┼───────────┘
            │                     │
            ▼                     ▼
┌─────────────────┐     ┌─────────────────┐
│  RDS MariaDB    │     │  AWS Bedrock    │
│  (Private)      │     │  (IAM Role)     │
└─────────────────┘     └─────────────────┘
            │
            ▼
┌─────────────────┐
│  DynamoDB       │
│  (On-Demand)    │
└─────────────────┘
```

### Kubernetes Deployment

**Service A: deployment.yaml**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: insight-core-service
spec:
  replicas: 3
  template:
    spec:
      serviceAccountName: insight-core-sa
      containers:
      - name: insight-core
        image: insight-core:latest
        env:
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: url
        - name: SERVICE_B_BASE_URL
          value: "http://ai-agent-service:8000"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
```

**Service B: deployment.yaml**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-agent-service
spec:
  replicas: 2
  template:
    spec:
      serviceAccountName: ai-agent-sa
      containers:
      - name: ai-agent
        image: ai-agent:latest
        env:
        - name: AWS_REGION
          value: "us-east-1"
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: host
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

## Monitoring & Observability

### Metrics

**Service A (Micrometer)**
```java
@Component
@RequiredArgsConstructor
public class MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    public void recordApiCall(String endpoint, long duration, boolean success) {
        Timer.builder("api.call")
            .tag("endpoint", endpoint)
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS);
    }
    
    public void recordBatchExecution(String batchName, long duration) {
        Timer.builder("batch.execution")
            .tag("batch", batchName)
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS);
    }
}
```

### Logging

**Structured Logging**
```java
@Slf4j
public class StructuredLogger {
    
    public void logApiRequest(String endpoint, String userId, String traceId) {
        log.info("API 요청 | endpoint={} | userId={} | traceId={}", 
                endpoint, maskUserId(userId), traceId);
    }
    
    public void logBatchStart(String batchName, int targetUserCount) {
        log.info("배치 시작 | batch={} | targetUsers={}", 
                batchName, targetUserCount);
    }
    
    public void logServiceBCall(String endpoint, long duration, boolean success) {
        log.info("Service B 호출 | endpoint={} | duration={}ms | success={}", 
                endpoint, duration, success);
    }
}
```

### Distributed Tracing

- Micrometer Tracing (Service A)
- 모든 로그에 TraceID 자동 부여
- Service A → Service B 호출 시 TraceID 전파
- CloudWatch Logs Insights로 트레이스 추적

## Performance Optimization

### Database Query Optimization

**인덱스 전략**
```sql
-- 사용자별 시간 범위 조회 최적화
CREATE INDEX idx_user_action_logs_user_time 
ON user_action_logs(user_id, action_time);

-- 주제별 통계 조회 최적화
CREATE INDEX idx_user_action_logs_topic 
ON user_action_logs(user_id, goals_id, action_type);
```

**쿼리 최적화**
```java
// Bad: N+1 문제
List<User> users = userRepository.findAll();
for (User user : users) {
    List<ActionLog> logs = actionLogRepository.findByUserId(user.getId());
}

// Good: Batch 조회
@Query("SELECT a FROM ActionLogEntity a WHERE a.userId IN :userIds")
List<ActionLogEntity> findByUserIds(@Param("userIds") List<String> userIds);
```

### Caching Strategy

**DynamoDB as Cache**
- AI Report는 생성 후 DynamoDB에 캐싱
- 프론트엔드는 캐시된 데이터만 조회
- TTL 설정으로 오래된 리포트 자동 삭제

**Application-Level Cache**
```java
@Cacheable(value = "dailyCheer", key = "#userId")
public DailyCheerResponse getDailyCheer(String userId) {
    // 캐시 미스 시에만 실행
    return dynamoDBRepository.getReport(userId, "DAILY_CHEER")
        .orElse(getDefaultCheer());
}
```

### Async Processing

**CompletableFuture를 활용한 병렬 처리**
```java
public AIReportRequest prepareReportData(String userId) {
    CompletableFuture<GrowthFeedback> growthFuture = 
        analyticsService.calculateGrowthRate(userId);
    CompletableFuture<TimelineFeedback> timelineFuture = 
        analyticsService.calculateTimeline(userId);
    CompletableFuture<PatternFeedback> patternFuture = 
        analyticsService.analyzePostponePattern(userId);
    CompletableFuture<SummaryFeedback> summaryFuture = 
        analyticsService.generateSummary(userId);
    
    // 모든 작업 완료 대기
    CompletableFuture.allOf(
        growthFuture, timelineFuture, patternFuture, summaryFuture
    ).join();
    
    return AIReportRequest.builder()
        .growth(growthFuture.join())
        .timeline(timelineFuture.join())
        .pattern(patternFuture.join())
        .summary(summaryFuture.join())
        .build();
}
```

## Conclusion

본 설계 문서는 Polyglot MSA 아키텍처를 기반으로 한 Todo Analytics & Report 서비스의 전체 시스템 설계를 정의합니다. Hexagonal Architecture를 통한 확장 가능한 구조, AWS Bedrock을 활용한 AI 통합, 그리고 Property-Based Testing을 통한 정확성 검증을 핵심으로 합니다.

주요 설계 원칙:
- 비즈니스 로직과 인프라 계층의 명확한 분리
- Phase 1(동기) → Phase 2(비동기) 전환 가능한 유연한 아키텍처
- Context Injection과 MCP 방식의 하이브리드 AI 통합
- 포괄적인 테스트 전략을 통한 품질 보증
- 보안과 성능을 고려한 프로덕션 준비 설계
