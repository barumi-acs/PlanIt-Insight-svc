# Requirements Document

## Introduction

이 문서는 PlanIt Todo Analytics & Report 서비스의 요구사항을 정의합니다. 본 서비스는 사용자의 할 일(Task) 처리 데이터를 분석하여 AI 기반 피드백 리포트를 생성하고, 사용자에게 개인화된 인사이트를 제공하는 마이크로서비스입니다.

## Glossary

- **System**: Todo Analytics & Report Service (Polyglot MSA)
- **Service_A**: Insight Core Service (Java 17, Spring Boot) - 로그 적재, API 서빙, 배치 스케줄러
- **Service_B**: AI Agent Service (Python 3.11, FastAPI) - AI 리포트 생성, MCP 챗봇
- **Task_Service**: 할 일 관리를 담당하는 마이크로서비스
- **User**: PlanIt 서비스를 사용하는 최종 사용자
- **Task**: 사용자가 생성한 할 일 항목
- **Action_Log**: 사용자의 Task 완료/미루기 행동 기록
- **Task_Postpone_Log**: Task Service에서 관리하는 미룸 이력 테이블
- **AI_Report**: AI가 생성한 분석 리포트 완제품
- **RDB**: MariaDB 기반 관계형 데이터베이스
- **NoSQL**: DynamoDB 기반 문서 데이터베이스
- **Batch_Scheduler**: 주기적으로 실행되는 백그라운드 작업 스케줄러 (ShedLock 적용)
- **Daily_Cheer**: 요일별 응원 메시지
- **Dashboard**: 성장 격려, 타임라인, 미룸 패턴, 종합 피드백을 포함한 대시보드
- **Growth_Feedback**: 특정 주제에서의 성장률 피드백
- **Timeline_Feedback**: 월별 수행률 추이 차트 데이터
- **Pattern_Feedback**: 요일별 미룸 패턴 분석 결과
- **Summary_Feedback**: 종합 피드백 메시지
- **ActionLogPort**: Task Service에서 Service A로 로그를 전송하기 위한 인터페이스
- **Phase_1**: 동기식 REST API 호출 방식의 초기 구현 단계
- **Phase_2**: 이벤트 기반 비동기 메시지 브로커(SQS/Kafka) 방식의 확장 단계
- **Hexagonal_Architecture**: 비즈니스 로직과 외부 의존성을 분리하는 Port & Adapter 패턴
- **AWS_Bedrock**: AWS의 관리형 AI 서비스 (Claude 3.5 Sonnet v2 사용)
- **MCP**: Model Context Protocol - AI가 도구를 사용하여 데이터를 조회하는 프로토콜
- **Context_Injection**: Service A가 데이터를 조회하여 Service B에 전달하는 방식
- **Tool_Use**: Bedrock의 Function Calling 기능
- **Prompt_Chaining**: 여러 단계의 프롬프트를 연결하여 복잡한 작업을 수행하는 기법

## Requirements

### Requirement 1: 사용자 행동 로그 저장

**User Story:** 시스템 관리자로서, 사용자의 할 일 처리 행동을 추적하고 싶습니다. 이를 통해 향후 분석 및 AI 피드백 생성의 기반 데이터를 확보할 수 있습니다.

#### Acceptance Criteria

1. WHEN 사용자가 Task를 완료 처리하면, THE System SHALL Action_Log를 RDB에 저장한다
2. WHEN 사용자가 Task를 미루기 처리하면, THE System SHALL Action_Log를 RDB에 저장한다
3. WHEN Action_Log를 저장할 때, THE System SHALL action_type을 'COMPLETED' 또는 'POSTPONED'로 기록한다
4. WHEN Action_Log를 저장할 때, THE System SHALL day_of_week와 hour_of_day를 자동으로 계산하여 저장한다
5. WHEN Task가 미루기 처리되면, THE System SHALL postponed_to_date 필드에 새로운 목표일을 기록한다

### Requirement 2: 요일별 수행률 분석

**User Story:** 사용자로서, 내가 어느 요일에 할 일을 잘 완수하는지 알고 싶습니다. 이를 통해 나의 생산성 패턴을 이해할 수 있습니다.

#### Acceptance Criteria

1. WHEN Batch_Scheduler가 실행되면, THE System SHALL 각 User의 요일별 완료율을 계산한다
2. WHEN 요일별 완료율을 계산할 때, THE System SHALL 최근 3개월 데이터를 기준으로 한다
3. WHEN 요일별 완료율을 계산할 때, THE System SHALL (완료 건수 / 전체 Task 건수) * 100으로 계산한다
4. WHEN 특정 요일의 데이터가 5건 미만이면, THE System SHALL 해당 요일의 통계를 제외한다

### Requirement 3: 목표 대비 달성률 계산

**User Story:** 사용자로서, 내가 설정한 목표 대비 실제 달성률을 알고 싶습니다. 이를 통해 나의 계획 수립 능력을 개선할 수 있습니다.

#### Acceptance Criteria

1. WHEN Batch_Scheduler가 실행되면, THE System SHALL 각 User의 주간 달성률을 계산한다
2. WHEN Batch_Scheduler가 실행되면, THE System SHALL 각 User의 월간 달성률을 계산한다
3. WHEN 달성률을 계산할 때, THE System SHALL (완료된 Task 수 / 전체 Task 수) * 100으로 계산한다
4. WHEN 특정 주제(goals_id)가 지정된 경우, THE System SHALL 주제별 달성률을 별도로 계산한다

### Requirement 4: 미룸 패턴 분석

**User Story:** 사용자로서, 내가 어떤 패턴으로 할 일을 미루는지 알고 싶습니다. 이를 통해 미루는 습관을 개선할 수 있습니다.

#### Acceptance Criteria

1. WHEN Batch_Scheduler가 실행되면, THE System SHALL 각 User의 요일별 미룸 횟수를 집계한다
2. WHEN 미룸 패턴을 분석할 때, THE System SHALL 가장 미룸이 많은 요일을 식별한다
3. WHEN 미룸 패턴을 분석할 때, THE System SHALL 평균 미룸 횟수를 계산한다
4. WHEN 미룸 패턴을 분석할 때, THE System SHALL 최근 1개월 데이터를 기준으로 한다

### Requirement 5: 주간 리포트 생성

**User Story:** 사용자로서, 매주 나의 할 일 수행 현황을 요약한 리포트를 받고 싶습니다. 이를 통해 주간 생산성을 점검할 수 있습니다.

#### Acceptance Criteria

1. WHEN Batch_Scheduler가 주간 리포트 생성 시점에 도달하면, THE System SHALL 각 User의 주간 리포트를 생성한다
2. WHEN 주간 리포트를 생성할 때, THE System SHALL 해당 주의 전체 진행률을 포함한다
3. WHEN 주간 리포트를 생성할 때, THE System SHALL AI_Report를 NoSQL에 저장한다
4. WHEN User의 일 평균 Task 수가 3개 미만이면, THE System SHALL 주간 리포트 생성을 건너뛴다

### Requirement 6: 월간 리포트 생성

**User Story:** 사용자로서, 매월 나의 할 일 수행 현황과 성장 추이를 담은 리포트를 받고 싶습니다. 이를 통해 장기적인 생산성 변화를 파악할 수 있습니다.

#### Acceptance Criteria

1. WHEN Batch_Scheduler가 월간 리포트 생성 시점에 도달하면, THE System SHALL 각 User의 월간 리포트를 생성한다
2. WHEN 월간 리포트를 생성할 때, THE System SHALL 성장 격려, 타임라인, 미룸 패턴, 종합 피드백을 포함한다
3. WHEN 월간 리포트를 생성할 때, THE System SHALL AI_Report를 NoSQL에 저장한다
4. WHEN User의 일 평균 Task 수가 3개 미만이면, THE System SHALL 월간 리포트 생성을 건너뛴다
5. WHEN 월간 리포트를 생성할 때, THE System SHALL 이전 3개월 데이터와 비교하여 성장률을 계산한다

### Requirement 7: AI 기반 피드백 문장 생성

**User Story:** 사용자로서, 단순한 수치가 아닌 AI가 생성한 자연스러운 피드백 문장을 받고 싶습니다. 이를 통해 더 동기부여를 받을 수 있습니다.

#### Acceptance Criteria

1. WHEN 리포트를 생성할 때, THE Service_A SHALL Service_B의 AI 리포트 생성 API를 호출한다
2. WHEN Service_B가 AI 리포트 생성 요청을 받으면, THE Service_B SHALL AWS_Bedrock을 호출하여 피드백 문장을 생성한다
3. WHEN AWS_Bedrock을 호출할 때, THE Service_B SHALL Claude 3.5 Sonnet v2 모델을 사용한다
4. WHEN AI 피드백을 생성할 때, THE Service_B SHALL Prompt_Chaining 기법을 사용하여 규격화된 JSON을 생성한다
5. WHEN AWS_Bedrock 호출이 실패하면, THE Service_B SHALL 기본 템플릿 메시지를 반환한다
6. WHEN AI 피드백 생성 시간이 10초를 초과하면, THE Service_A SHALL 타임아웃 처리한다

### Requirement 8: 수행 패턴 기반 계획 재조정 권유

**User Story:** 사용자로서, 내가 계획을 너무 많이 세우거나 실행력이 부족할 때 알림을 받고 싶습니다. 이를 통해 현실적인 계획을 수립할 수 있습니다.

#### Acceptance Criteria

1. WHEN User의 주간 완료율이 50% 미만이고 Task 수가 10개 이상이면, THE System SHALL 계획 재조정 권유 메시지를 생성한다
2. WHEN 계획 재조정 권유 메시지를 생성할 때, THE System SHALL 현재 완료율과 권장 Task 수를 포함한다
3. WHEN 계획 재조정 권유가 발생하면, THE System SHALL 해당 메시지를 AI_Report에 포함한다

### Requirement 9: 일간 응원 피드백 조회 API

**User Story:** 사용자로서, 홈 화면에서 오늘의 요일별 응원 메시지를 보고 싶습니다. 이를 통해 하루를 긍정적으로 시작할 수 있습니다.

#### Acceptance Criteria

1. WHEN User가 일간 응원 피드백 조회 API를 호출하면, THE System SHALL NoSQL에서 해당 User의 Daily_Cheer 데이터를 조회한다
2. WHEN Daily_Cheer 데이터를 조회할 때, THE System SHALL 오늘 날짜와 요일 정보를 포함한다
3. WHEN Daily_Cheer 데이터를 조회할 때, THE System SHALL 평균 대비 수행률 차이를 포함한다
4. WHEN Daily_Cheer 데이터가 존재하지 않으면, THE System SHALL 기본 응원 메시지를 반환한다
5. WHEN API 호출이 성공하면, THE System SHALL ApiResponse 공통 포맷으로 응답한다

### Requirement 10: AI 피드백 대시보드 조회 API

**User Story:** 사용자로서, 리포트 탭에서 나의 종합적인 분석 결과를 한눈에 보고 싶습니다. 이를 통해 나의 생산성 현황을 파악할 수 있습니다.

#### Acceptance Criteria

1. WHEN User가 대시보드 조회 API를 호출하면, THE System SHALL NoSQL에서 해당 User의 AI_Report를 조회한다
2. WHEN 대시보드를 조회할 때, THE System SHALL yearMonth와 week 파라미터를 필수로 받는다
3. WHEN 대시보드를 조회할 때, THE System SHALL Growth_Feedback, Timeline_Feedback, Pattern_Feedback, Summary_Feedback를 포함한다
4. WHEN 해당 기간의 AI_Report가 존재하지 않으면, THE System SHALL IS4041 에러 코드를 반환한다
5. WHEN API 호출이 성공하면, THE System SHALL ApiResponse 공통 포맷으로 응답한다

### Requirement 11: 데이터 저장 및 조회 성능 최적화

**User Story:** 시스템 관리자로서, 대량의 로그 데이터를 효율적으로 저장하고 조회하고 싶습니다. 이를 통해 서비스 응답 속도를 보장할 수 있습니다.

#### Acceptance Criteria

1. WHEN Action_Log를 저장할 때, THE System SHALL user_id와 action_time에 대한 복합 인덱스를 사용한다
2. WHEN 주제별 통계를 조회할 때, THE System SHALL user_id, goals_id, action_type에 대한 복합 인덱스를 사용한다
3. WHEN AI_Report를 조회할 때, THE System SHALL NoSQL의 PK와 SK를 사용하여 단일 쿼리로 조회한다
4. WHEN 대시보드 API 응답 시간이 500ms를 초과하면, THE System SHALL 경고 로그를 기록한다

### Requirement 12: 에러 처리 및 로깅

**User Story:** 시스템 관리자로서, 서비스 장애 발생 시 원인을 빠르게 파악하고 싶습니다. 이를 통해 서비스 안정성을 유지할 수 있습니다.

#### Acceptance Criteria

1. WHEN API 호출 중 예외가 발생하면, THE System SHALL GlobalExceptionHandler를 통해 공통 에러 포맷으로 응답한다
2. WHEN 분석할 데이터가 부족하면, THE System SHALL IS4041 에러 코드를 반환한다
3. WHEN AI 에이전트 호출이 실패하면, THE System SHALL AI5001 에러 코드를 반환한다
4. WHEN 모든 API 요청에 대해, THE System SHALL TraceID를 포함한 로그를 기록한다
5. WHEN 에러가 발생하면, THE System SHALL 스택트레이스를 서버 로그에만 기록하고 클라이언트에는 일반화된 메시지를 반환한다

### Requirement 13: 인증 및 권한 관리

**User Story:** 사용자로서, 내 데이터가 다른 사용자에게 노출되지 않기를 원합니다. 이를 통해 개인정보를 보호할 수 있습니다.

#### Acceptance Criteria

1. WHEN User가 API를 호출할 때, THE System SHALL JWT 토큰을 검증한다
2. WHEN JWT 토큰이 유효하지 않으면, THE System SHALL C4011 에러 코드를 반환한다
3. WHEN User가 다른 User의 리포트를 조회하려 하면, THE System SHALL 접근을 거부한다
4. WHEN API 호출 시, THE System SHALL JWT에서 추출한 user_id로 데이터를 필터링한다

### Requirement 14: Soft Delete 적용

**User Story:** 시스템 관리자로서, 실수로 삭제된 데이터를 복구하고 싶습니다. 이를 통해 데이터 손실을 방지할 수 있습니다.

#### Acceptance Criteria

1. WHEN Action_Log 엔티티를 정의할 때, THE System SHALL BaseTimeEntity를 상속받는다
2. WHEN Action_Log 엔티티를 정의할 때, THE System SHALL @SQLDelete와 @SQLRestriction 어노테이션을 적용한다
3. WHEN 데이터 삭제 요청이 발생하면, THE System SHALL deleted_at 컬럼에 현재 시간을 기록한다
4. WHEN 데이터를 조회할 때, THE System SHALL deleted_at이 NULL인 데이터만 반환한다

### Requirement 15: MSA 간 통신 아키텍처 (Phase 1 - 동기식)

**User Story:** 시스템 아키텍트로서, Task Service와 Insight Service 간의 통신을 구현하되, 향후 비동기 방식으로 전환할 수 있도록 확장 가능한 구조를 원합니다. 이를 통해 인프라 변경 시 비즈니스 로직 수정을 최소화할 수 있습니다.

#### Acceptance Criteria

1. WHEN Task Service에서 로그 전송이 필요할 때, THE Task_Service SHALL ActionLogPort 인터페이스를 호출한다
2. WHEN Phase_1 구현 시, THE Task_Service SHALL ActionLogPort의 구현체로 REST API 또는 gRPC 클라이언트를 사용한다
3. WHEN ActionLogPort 인터페이스를 정의할 때, THE Task_Service SHALL 통신 방식에 독립적인 메서드 시그니처를 사용한다
4. WHEN 비즈니스 로직을 작성할 때, THE Task_Service SHALL ActionLogPort 인터페이스에만 의존하고 구현체에 직접 의존하지 않는다
5. WHERE Phase_2로 전환할 때, THE Task_Service SHALL ActionLogPort의 구현체만 교체하여 메시지 브로커 방식으로 전환할 수 있다

### Requirement 16: Task Service의 자체 이력 저장

**User Story:** Task Service 개발자로서, 사용자가 할 일을 미루기 처리할 때 자체 데이터베이스에 이력을 저장하고 싶습니다. 이를 통해 Task Service의 데이터 무결성을 보장할 수 있습니다.

#### Acceptance Criteria

1. WHEN 사용자가 Task를 미루기 처리하면, THE Task_Service SHALL tasks 테이블의 상태를 업데이트한다
2. WHEN tasks 테이블 업데이트가 성공하면, THE Task_Service SHALL Task_Postpone_Log 테이블에 이력을 INSERT 한다
3. WHEN Task_Postpone_Log를 저장할 때, THE Task_Service SHALL log_id, task_id, postponed_at, original_due_date, new_due_date를 포함한다
4. WHEN Task_Postpone_Log 저장이 실패하면, THE Task_Service SHALL 전체 트랜잭션을 롤백한다

### Requirement 17: Task Service에서 Insight Service로 로그 전송 (Phase 1)

**User Story:** Task Service 개발자로서, 할 일 처리 이력을 Insight Service로 전송하여 분석 데이터를 제공하고 싶습니다. 이를 통해 사용자에게 인사이트를 제공할 수 있습니다.

#### Acceptance Criteria

1. WHEN Task Service의 DB 트랜잭션이 성공적으로 완료되면, THE Task_Service SHALL ActionLogPort를 호출하여 로그 데이터를 전송한다
2. WHEN ActionLogPort를 호출할 때, THE Task_Service SHALL user_id, task_id, goals_id, action_type, action_time, due_date, postponed_to_date를 포함한다
3. WHEN Phase_1 구현 시, THE ActionLogPort 구현체 SHALL Insight_Service의 REST API를 동기적으로 호출한다
4. WHEN Insight_Service 호출이 실패하면, THE Task_Service SHALL 에러를 로깅하되 사용자 요청은 성공으로 처리한다
5. WHEN Insight_Service 호출 시간이 3초를 초과하면, THE Task_Service SHALL 타임아웃 처리하고 에러를 로깅한다

### Requirement 18: Insight Service의 로그 수신 API

**User Story:** Insight Service 개발자로서, Task Service로부터 로그 데이터를 수신하여 분석 데이터베이스에 저장하고 싶습니다. 이를 통해 사용자 행동 분석의 기반 데이터를 확보할 수 있습니다.

#### Acceptance Criteria

1. WHEN Insight_Service가 Task_Service로부터 로그 전송 API 요청을 받으면, THE Insight_Service SHALL 요청 데이터의 유효성을 검증한다
2. WHEN 요청 데이터가 유효하면, THE Insight_Service SHALL 데이터를 파싱하고 포맷팅한다
3. WHEN 데이터 파싱이 완료되면, THE Insight_Service SHALL user_action_logs 테이블에 INSERT 한다
4. WHEN user_action_logs에 저장할 때, THE Insight_Service SHALL day_of_week와 hour_of_day를 자동으로 계산한다
5. WHEN 저장이 성공하면, THE Insight_Service SHALL 200 OK 응답을 반환한다
6. WHEN 저장이 실패하면, THE Insight_Service SHALL IS5001 에러 코드를 반환한다

### Requirement 19: 아키텍처 확장성 보장 (Phase 2 대비)

**User Story:** 시스템 아키텍트로서, 향후 메시지 브로커 기반 비동기 통신으로 전환할 때 비즈니스 로직 변경 없이 구현체만 교체하고 싶습니다. 이를 통해 시스템 확장성과 유지보수성을 향상시킬 수 있습니다.

#### Acceptance Criteria

1. WHEN ActionLogPort 인터페이스를 설계할 때, THE System SHALL 동기/비동기 방식 모두에 적용 가능한 추상화 수준을 유지한다
2. WHEN Phase_2로 전환할 때, THE System SHALL ActionLogPort의 새로운 구현체(SQS/Kafka Publisher)를 추가한다
3. WHEN Phase_2로 전환할 때, THE System SHALL 비즈니스 로직(Service Layer)의 코드 수정 없이 구현체만 교체한다
4. WHEN Phase_2로 전환할 때, THE Insight_Service SHALL 메시지 브로커로부터 이벤트를 구독하는 Consumer를 추가한다
5. WHERE Phase_2 구현 시, THE System SHALL 기존 REST API 엔드포인트를 유지하여 하위 호환성을 보장할 수 있다

### Requirement 20: API 문서화 및 테스트 환경 (Swagger UI)

**User Story:** 개발자로서, REST API를 문서화하고 브라우저에서 직접 테스트하고 싶습니다. 이를 통해 API 스펙을 쉽게 이해하고 통합 테스트를 수행할 수 있습니다.

#### Acceptance Criteria

1. WHEN 애플리케이션이 시작되면, THE System SHALL Swagger UI를 활성화한다
2. WHEN Swagger UI에 접속하면, THE System SHALL 모든 REST API 엔드포인트를 표시한다
3. WHEN API 엔드포인트를 정의할 때, THE System SHALL @Operation, @ApiResponse, @Parameter 어노테이션을 사용하여 문서화한다
4. WHEN Swagger UI에서 API를 테스트할 때, THE System SHALL JWT 토큰 인증을 지원한다
5. WHEN Swagger UI를 통해 요청을 보내면, THE System SHALL 실제 API와 동일하게 동작한다
6. WHEN API 응답 스키마를 정의할 때, THE System SHALL 예시 데이터를 포함한다

### Requirement 21: 배치 스케줄러 동시성 제어

**User Story:** 시스템 관리자로서, 배치 작업이 중복 실행되지 않도록 제어하고 싶습니다. 이를 통해 데이터 정합성을 보장할 수 있습니다.

#### Acceptance Criteria

1. WHEN Batch_Scheduler를 구현할 때, THE Service_A SHALL ShedLock 라이브러리를 사용한다
2. WHEN 배치 작업이 실행 중일 때, THE Service_A SHALL 다른 인스턴스에서 동일한 작업이 실행되지 않도록 락을 획득한다
3. WHEN 배치 작업이 완료되면, THE Service_A SHALL 락을 해제한다
4. WHEN 배치 작업 실행 중 예외가 발생하면, THE Service_A SHALL 락을 자동으로 해제한다

### Requirement 22: 통계 데이터 병렬 조회

**User Story:** 시스템 관리자로서, 여러 통계 데이터를 병렬로 조회하여 배치 처리 시간을 단축하고 싶습니다. 이를 통해 시스템 효율성을 향상시킬 수 있습니다.

#### Acceptance Criteria

1. WHEN 배치에서 4가지 통계를 조회할 때, THE Service_A SHALL CompletableFuture 또는 @Async를 사용하여 병렬 처리한다
2. WHEN 병렬 조회를 수행할 때, THE Service_A SHALL 각 통계 조회를 독립적으로 실행한다
3. WHEN 모든 병렬 조회가 완료되면, THE Service_A SHALL 결과를 수집하여 Service_B로 전송한다
4. WHEN 병렬 조회 중 일부가 실패하면, THE Service_A SHALL 성공한 데이터만으로 리포트를 생성한다

### Requirement 23: Service B - AI 리포트 생성 API

**User Story:** AI Service 개발자로서, Service A로부터 통계 데이터를 받아 AI 리포트를 생성하고 싶습니다. 이를 통해 사용자에게 인사이트를 제공할 수 있습니다.

#### Acceptance Criteria

1. WHEN Service_B가 리포트 생성 요청을 받으면, THE Service_B SHALL 요청 데이터의 유효성을 검증한다
2. WHEN 데이터가 유효하면, THE Service_B SHALL AWS_Bedrock Converse API를 호출한다
3. WHEN Bedrock을 호출할 때, THE Service_B SHALL boto3 또는 langchain-aws를 사용한다
4. WHEN Bedrock 응답을 받으면, THE Service_B SHALL 규격화된 JSON 형태로 파싱한다
5. WHEN 파싱이 완료되면, THE Service_B SHALL Service_A에 JSON 응답을 반환한다
6. WHEN Bedrock 호출이 실패하면, THE Service_B SHALL AI5001 에러 코드를 반환한다

### Requirement 24: MCP 기반 딥다이브 챗봇

**User Story:** 사용자로서, 자연어로 질문하여 내 데이터에 대한 심층 분석을 받고 싶습니다. 이를 통해 더 상세한 인사이트를 얻을 수 있습니다.

#### Acceptance Criteria

1. WHEN 사용자가 자연어 질의를 입력하면, THE Service_A SHALL Service_B의 챗봇 API로 요청을 전달한다
2. WHEN Service_B가 챗봇 요청을 받으면, THE Service_B SHALL MCP 로직을 실행한다
3. WHEN MCP 로직이 실행되면, THE Service_B SHALL Bedrock Tool_Use 기능을 활성화한다
4. WHEN Claude 모델이 데이터 조회가 필요하다고 판단하면, THE Service_B SHALL 정의된 도구를 호출하여 MariaDB를 조회한다
5. WHEN 데이터 조회가 완료되면, THE Service_B SHALL Claude 모델에 결과를 전달하여 최종 답변을 생성한다
6. WHEN 최종 답변이 생성되면, THE Service_B SHALL Service_A를 통해 사용자에게 응답을 반환한다

### Requirement 25: 환경 변수 기반 설정 관리

**User Story:** DevOps 엔지니어로서, 환경별로 다른 설정을 코드 수정 없이 적용하고 싶습니다. 이를 통해 배포 유연성을 확보할 수 있습니다.

#### Acceptance Criteria

1. WHEN Service_A를 구성할 때, THE Service_A SHALL application.yml에서 환경 변수를 주입받는다
2. WHEN Service_B를 구성할 때, THE Service_B SHALL .env 파일에서 환경 변수를 주입받는다
3. WHEN DB 접속 정보를 설정할 때, THE System SHALL 코드에 하드코딩하지 않는다
4. WHEN AWS Bedrock을 호출할 때, THE Service_B SHALL AWS Default Credential Provider Chain을 사용한다
5. WHEN 로컬 환경에서 실행할 때, THE System SHALL 로컬 환경 변수를 사용한다
6. WHEN 클라우드 환경에서 실행할 때, THE System SHALL IAM Role 기반 인증을 사용한다
