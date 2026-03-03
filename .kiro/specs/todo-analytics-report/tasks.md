# Implementation Plan: Todo Analytics & Report Service

## Overview

본 구현 계획은 Polyglot MSA 아키텍처(Java + Python)를 기반으로 한 Todo Analytics & Report 서비스의 단계별 구현 작업을 정의합니다. Service A(Java)와 Service B(Python)를 순차적으로 구현하며, 각 단계에서 테스트를 통해 검증합니다.

## Tasks

- [x] 1. Service A: 프로젝트 초기 설정 및 공통 모듈 구성
  - Spring Boot 프로젝트 구조 설정
  - build.gradle에 필요한 의존성 추가 (Spring Data JPA, MariaDB, DynamoDB SDK, ShedLock, jqwik, Swagger)
  - application.yml 환경 변수 설정
  - BaseTimeEntity, ApiResponse, ErrorCode, CustomException, GlobalExceptionHandler 구현
  - _Requirements: 12.1, 12.2, 12.5, 20.1, 25.1, 25.3_

- [x] 2. Service A: MariaDB 엔티티 및 Repository 구현
  - [x] 2.1 ActionLogEntity 구현
    - BaseTimeEntity 상속
    - @SQLDelete, @SQLRestriction 어노테이션 적용
    - @PrePersist로 day_of_week, hour_of_day 자동 계산
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 14.1, 14.2_
  
  - [ ]* 2.2 ActionLogEntity Property Test 작성
    - **Property 3: 시간 정보 자동 계산**
    - **Validates: Requirements 1.4, 18.4**
  
  - [x] 2.3 ActionLogRepository 인터페이스 구현
    - findByUserIdAndDateRange 쿼리 메서드
    - calculateDayOfWeekStats 쿼리 메서드
    - 복합 인덱스 활용 쿼리 작성
    - _Requirements: 2.1, 2.2, 11.1, 11.2_
  
  - [ ]* 2.4 ActionLogRepository Unit Test 작성
    - 날짜 범위 조회 테스트
    - 요일별 통계 조회 테스트
    - _Requirements: 2.1, 2.2_

- [x] 3. Service A: DynamoDB Repository 구현
  - [x] 3.1 DynamoDBRepository 클래스 구현
    - DynamoDbClient 설정 (IAM Role 기반)
    - saveReport 메서드 (PutItem)
    - getReport 메서드 (GetItem with PK/SK)
    - _Requirements: 5.3, 6.3, 11.3, 25.4, 25.6_
  
  - [ ]* 3.2 DynamoDBRepository Property Test 작성
    - **Property 10: NoSQL 단일 쿼리 조회**
    - **Validates: Requirements 11.3**
  
  - [ ]* 3.3 DynamoDBRepository Unit Test 작성
    - 리포트 저장 테스트
    - 리포트 조회 테스트
    - _Requirements: 5.3, 6.3_

- [x] 4. Service A: Port 인터페이스 정의
  - [x] 4.1 ActionLogPort 인터페이스 정의
    - sendActionLog 메서드 시그니처
    - 통신 방식에 독립적인 추상화
    - _Requirements: 15.1, 15.3, 15.4_
  
  - [x] 4.2 AIReportPort 인터페이스 정의
    - generateReport 메서드 시그니처
    - _Requirements: 7.1, 23.1_

- [x] 5. Service A: Adapter 구현 (Phase 1)
  - [x] 5.1 RestApiAdapter 구현
    - RestTemplate 설정
    - AIReportPort 구현 (Service B 호출)
    - 타임아웃 설정 (10초)
    - 에러 처리 및 로깅
    - _Requirements: 7.6, 15.2, 17.3, 17.5_
  
  - [ ]* 5.2 RestApiAdapter Unit Test 작성
    - Service B 호출 성공 케이스
    - 타임아웃 케이스
    - 에러 처리 케이스
    - _Requirements: 7.6, 17.5_

- [ ] 6. Checkpoint - Service A 기본 인프라 검증
  - 모든 테스트 통과 확인
  - 사용자에게 진행 상황 공유 및 질문 확인

- [x] 7. Service A: Analytics Service 구현
  - [x] 7.1 AnalyticsService 클래스 구현
    - @Async 설정
    - calculateGrowthRate 메서드 (CompletableFuture)
    - calculateTimeline 메서드 (CompletableFuture)
    - analyzePostponePattern 메서드 (CompletableFuture)
    - generateSummary 메서드 (CompletableFuture)
    - _Requirements: 2.1, 2.2, 3.1, 3.2, 4.1, 4.2, 4.3, 22.1, 22.2_
  
  - [ ]* 7.2 AnalyticsService Property Test 작성
    - **Property 5: 요일별 완료율 계산 정확성**
    - **Validates: Requirements 2.3**
  
  - [ ]* 7.3 AnalyticsService Property Test 작성
    - **Property 6: 데이터 범위 제한**
    - **Validates: Requirements 2.2**
  
  - [ ]* 7.4 AnalyticsService Unit Test 작성
    - 병렬 조회 테스트
    - 통계 계산 정확성 테스트
    - _Requirements: 2.3, 22.1_

- [x] 8. Service A: Batch Scheduler 구현
  - [x] 8.1 ShedLock 설정
    - @EnableSchedulerLock 설정
    - LockProvider 빈 등록
    - _Requirements: 21.1, 21.2, 21.3, 21.4_
  
  - [x] 8.2 ReportGenerationScheduler 구현
    - generateWeeklyReports 메서드 (@Scheduled, @SchedulerLock)
    - generateMonthlyReports 메서드 (@Scheduled, @SchedulerLock)
    - 일 평균 Task 3개 미만 필터링
    - CompletableFuture.allOf로 병렬 조회
    - Service B 호출 및 DynamoDB 저장
    - _Requirements: 5.1, 5.2, 5.4, 6.1, 6.2, 6.4, 22.3_
  
  - [ ]* 8.3 ReportGenerationScheduler Unit Test 작성
    - 배치 실행 테스트
    - 중복 실행 방지 테스트
    - _Requirements: 5.1, 21.2_

- [x] 9. Service A: Action Log Service 구현
  - [x] 9.1 ActionLogService 클래스 구현
    - saveActionLog 메서드
    - 데이터 검증 로직
    - ActionLogEntity 변환 및 저장
    - _Requirements: 1.1, 1.2, 18.1, 18.2, 18.3_
  
  - [ ]* 9.2 ActionLogService Property Test 작성
    - **Property 1: Action Log 저장 완전성**
    - **Validates: Requirements 1.1, 1.2**
  
  - [ ]* 9.3 ActionLogService Property Test 작성
    - **Property 2: Action Type 유효성**
    - **Validates: Requirements 1.3**
  
  - [ ]* 9.4 ActionLogService Property Test 작성
    - **Property 15: 입력 데이터 검증**
    - **Validates: Requirements 18.1**
  
  - [ ]* 9.5 ActionLogService Unit Test 작성
    - 유효한 데이터 저장 테스트
    - 유효하지 않은 데이터 검증 테스트
    - _Requirements: 1.1, 18.1_

- [x] 10. Service A: Feedback Service 구현
  - [x] 10.1 FeedbackService 클래스 구현
    - getDailyCheer 메서드
    - getDashboard 메서드
    - DynamoDB 조회 및 기본값 처리
    - _Requirements: 9.1, 9.4, 10.1, 10.4_
  
  - [ ]* 10.2 FeedbackService Property Test 작성
    - **Property 8: 일간 응원 피드백 필수 필드**
    - **Validates: Requirements 9.2, 9.3**
  
  - [ ]* 10.3 FeedbackService Property Test 작성
    - **Property 9: 대시보드 피드백 완전성**
    - **Validates: Requirements 10.3**
  
  - [ ]* 10.4 FeedbackService Unit Test 작성
    - 데이터 존재 시 조회 테스트
    - 데이터 없을 시 기본값 테스트
    - _Requirements: 9.4, 10.4_

- [x] 11. Service A: Controller 구현
  - [x] 11.1 FeedbackController 구현
    - GET /api/v1/feedbacks/daily-cheer
    - GET /api/v1/feedbacks/dashboard
    - JWT 토큰 검증 (X-User-Id 헤더)
    - Swagger 어노테이션 추가
    - _Requirements: 9.1, 9.2, 9.3, 9.5, 10.1, 10.2, 10.3, 10.5, 13.1, 13.4, 20.3, 20.4, 20.6_
  
  - [x] 11.2 InternalActionLogController 구현
    - POST /internal/api/v1/action-logs
    - 요청 데이터 검증
    - ActionLogService 호출
    - _Requirements: 18.1, 18.5, 18.6_
  
  - [ ]* 11.3 Controller Integration Test 작성
    - API 호출 성공 케이스
    - 인증 실패 케이스
    - 파라미터 검증 케이스
    - _Requirements: 9.5, 10.5, 13.1_

- [ ] 12. Checkpoint - Service A 완료 검증
  - 모든 테스트 통과 확인
  - Swagger UI 접속 및 API 테스트
  - 사용자에게 진행 상황 공유 및 질문 확인

- [ ] 13. Service B: 프로젝트 초기 설정
  - FastAPI 프로젝트 구조 설정
  - requirements.txt 작성 (fastapi, boto3, pydantic, pytest, hypothesis)
  - .env 파일 설정
  - config.py 구현 (환경 변수 로드)
  - _Requirements: 25.2, 25.3, 25.5_

- [ ] 14. Service B: Pydantic Models 정의
  - [ ] 14.1 models.py 구현
    - ReportGenerationRequest
    - ReportGenerationResponse
    - ChatQueryRequest
    - ChatQueryResponse
    - _Requirements: 7.2, 23.1, 24.1_
  
  - [ ]* 14.2 Models Unit Test 작성
    - 데이터 검증 테스트
    - JSON 직렬화/역직렬화 테스트
    - _Requirements: 23.1_

- [ ] 15. Service B: Bedrock Client 설정
  - [ ] 15.1 bedrock_client.py 구현
    - boto3 클라이언트 생성 (Default Credential Provider Chain)
    - Claude 3.5 Sonnet v2 모델 설정
    - _Requirements: 7.2, 7.3, 23.2, 23.3, 25.4, 25.6_
  
  - [ ]* 15.2 Bedrock Client Unit Test 작성
    - 클라이언트 생성 테스트
    - _Requirements: 25.4_

- [ ] 16. Service B: Report Generator Service 구현
  - [ ] 16.1 report_generator_service.py 구현
    - generate_report 메서드
    - _generate_growth_feedback (Bedrock Converse API)
    - _generate_timeline_feedback
    - _generate_pattern_feedback
    - _generate_summary_feedback (Prompt Chaining)
    - JSON 파싱 및 에러 처리
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 23.2, 23.3, 23.4, 23.5_
  
  - [ ]* 16.2 Report Generator Service Unit Test 작성
    - Bedrock 호출 성공 케이스
    - Bedrock 호출 실패 케이스 (기본 템플릿)
    - JSON 파싱 테스트
    - _Requirements: 7.3, 7.5_

- [ ] 17. Service B: Chatbot Service 구현 (MCP)
  - [ ] 17.1 chatbot_service.py 구현
    - _define_tools 메서드 (Tool Use 정의)
    - process_query 메서드
    - _execute_tools 메서드 (MariaDB 조회)
    - _query_database 메서드
    - _calculate_rate 메서드
    - _has_tool_use, _extract_text 유틸리티
    - _Requirements: 24.2, 24.3, 24.4, 24.5_
  
  - [ ]* 17.2 Chatbot Service Unit Test 작성
    - Tool Use 정의 테스트
    - 도구 실행 테스트
    - 최종 답변 생성 테스트
    - _Requirements: 24.3, 24.4_

- [ ] 18. Service B: FastAPI Endpoints 구현
  - [ ] 18.1 main.py 구현
    - POST /ai/reports/generate
    - POST /ai/chat/query
    - 에러 핸들러 등록
    - CORS 설정
    - _Requirements: 7.1, 23.1, 23.6, 24.1, 24.6_
  
  - [ ]* 18.2 Endpoints Integration Test 작성
    - 리포트 생성 API 테스트
    - 챗봇 API 테스트
    - 에러 케이스 테스트
    - _Requirements: 23.5, 23.6_

- [ ] 19. Checkpoint - Service B 완료 검증
  - 모든 테스트 통과 확인
  - FastAPI Swagger UI 접속 및 API 테스트
  - 사용자에게 진행 상황 공유 및 질문 확인

- [ ] 20. Service A ↔ Service B 통합 테스트
  - [ ] 20.1 배치 리포트 생성 E2E 테스트
    - Service A 배치 실행
    - Service B 호출 확인
    - DynamoDB 저장 확인
    - _Requirements: 5.1, 6.1, 7.1, 22.3_
  
  - [ ]* 20.2 API 조회 E2E 테스트
    - 일간 응원 피드백 조회
    - 대시보드 조회
    - _Requirements: 9.1, 10.1_
  
  - [ ]* 20.3 MCP 챗봇 E2E 테스트
    - 자연어 질의 처리
    - Tool Use 실행 확인
    - _Requirements: 24.1, 24.4_

- [ ] 21. Soft Delete 기능 검증
  - [ ]* 21.1 Soft Delete Property Test 작성
    - **Property 11: Soft Delete 동작**
    - **Validates: Requirements 14.3, 14.4**
  
  - [ ]* 21.2 Soft Delete Unit Test 작성
    - 삭제 후 deleted_at 확인
    - 조회 시 삭제된 데이터 제외 확인
    - _Requirements: 14.3, 14.4_

- [ ] 22. 성능 및 보안 검증
  - [ ]* 22.1 성능 테스트
    - 대시보드 API 응답 시간 측정 (500ms 이하)
    - 배치 병렬 조회 성능 측정
    - _Requirements: 11.4, 22.1_
  
  - [ ]* 22.2 보안 테스트
    - JWT 토큰 검증 테스트
    - 다른 사용자 데이터 접근 차단 테스트
    - _Requirements: 13.1, 13.2, 13.3_

- [ ] 23. 문서화 및 배포 준비
  - README.md 작성 (로컬 실행 가이드)
  - Dockerfile 작성 (Service A, Service B)
  - Kubernetes Deployment YAML 작성
  - 환경 변수 설정 가이드 작성
  - _Requirements: 25.1, 25.2, 25.5, 25.6_

- [ ] 24. Final Checkpoint - 전체 시스템 검증
  - 모든 테스트 통과 확인
  - Swagger UI를 통한 전체 API 테스트
  - 로컬 환경에서 E2E 시나리오 실행
  - 사용자에게 최종 결과 공유

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- Service A(Java)와 Service B(Python)를 순차적으로 구현
- 각 서비스 완료 후 Checkpoint에서 검증
- 통합 테스트는 두 서비스 모두 완료 후 진행
