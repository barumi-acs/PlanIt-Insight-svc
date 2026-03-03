# 프로젝트 구조

## 디렉토리 구조

```
src/main/java/com/planit/basetemplate/
├── common/              # 공통 유틸리티 및 규격
│   ├── ApiResponse.java        # 공통 응답 포맷
│   ├── BaseTimeEntity.java     # 시간 자동화 베이스 엔티티
│   ├── CustomException.java    # 커스텀 예외
│   ├── ErrorCode.java          # 에러 코드 정의
│   └── GlobalExceptionHandler.java  # 전역 예외 처리
├── domain/              # 도메인별 기능 (예: TestController)
├── grpc/                # gRPC 서비스 구현
│   └── BaseGrpcController.java
├── sample/              # 샘플 코드 (삭제 예정)
│   ├── SampleController.java
│   ├── SampleData.java
│   ├── SampleRepository.java
│   └── SampleService.java
└── PlanitBaseTemplateApplication.java  # 메인 애플리케이션

src/main/proto/          # Protobuf 정의 파일
└── base_test.proto

src/main/resources/
├── application.yml      # 애플리케이션 설정
└── logback-spring.xml   # 로깅 설정
```

## 아키텍처 패턴

### 레이어 구조
- Controller: REST API 엔드포인트 및 gRPC 서비스
- Service: 비즈니스 로직
- Repository: 데이터 접근 계층 (JPA)
- Entity: 데이터베이스 테이블 매핑

### 공통 모듈 (common/)
모든 도메인에서 공유하는 공통 기능:
- 응답 포맷 (ApiResponse)
- 에러 처리 (CustomException, ErrorCode, GlobalExceptionHandler)
- 시간 자동화 (BaseTimeEntity)

### 도메인 구조
각 도메인은 독립적인 패키지로 구성:
```
domain/
├── DomainController.java
├── DomainService.java
├── DomainRepository.java
└── DomainEntity.java
```

## 생성된 코드 위치

### Protobuf 생성 코드
```
build/generated/source/proto/main/
├── grpc/    # gRPC 서비스 스텁
└── java/    # Protobuf 메시지 클래스
```

## 설정 파일

- build.gradle: 의존성 및 빌드 설정
- settings.gradle: 프로젝트 설정
- application.yml: 애플리케이션 환경 설정
- logback-spring.xml: 로깅 포맷 및 레벨 설정
- dockerfile: 컨테이너 이미지 빌드 설정
