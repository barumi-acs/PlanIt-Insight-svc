# Swagger UI 수정 완료

## 문제 상황
1. `io.swagger` import 오류 (IDE 캐시 문제)
2. Swagger UI 접속 시 "Failed to load remote configuration" 오류
3. `/v3/api-docs` 및 `/api-docs` 엔드포인트 500/403 오류

## 원인 분석
1. **Bean Validation 누락**: `jakarta.validation` 프로바이더가 없어 Swagger가 컨트롤러 스캔 실패
2. **Security 설정 누락**: 커스텀 Swagger 경로(`/api-docs`)가 SecurityConfig에서 차단됨
3. **RestTemplateConfig 경고**: Spring Boot 3.4+ deprecated 메서드 사용

## 해결 방법

### 1. Bean Validation 의존성 추가
```gradle
// build.gradle
implementation 'org.springframework.boot:spring-boot-starter-validation'
```

### 2. SecurityConfig 수정
```java
// SecurityConfig.java
.requestMatchers(
    "/swagger-ui/**",
    "/v3/api-docs/**",
    "/api-docs/**",  // Custom Swagger path 추가
    "/swagger-resources/**",
    "/webjars/**"
).permitAll()
```

### 3. RestTemplateConfig 경고 수정
```java
// RestTemplateConfig.java
// Before (deprecated)
builder
    .setConnectTimeout(Duration.ofSeconds(5))
    .setReadTimeout(Duration.ofSeconds(10))

// After (권장)
ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
    .withConnectTimeout(Duration.ofSeconds(5))
    .withReadTimeout(Duration.ofSeconds(10));

builder.requestFactory(() -> ClientHttpRequestFactories.get(settings))
```

## 검증 결과
```bash
# Swagger UI 접근 성공
curl http://localhost:8084/swagger-ui/index.html
# StatusCode: 200

# API Docs 접근 성공
curl http://localhost:8084/api-docs
# StatusCode: 200, OpenAPI 3.1.0 JSON 반환

# REST API 정상 작동
curl http://localhost:8084/api/v1/base/test
# StatusCode: 200
```

## 접속 URL
- Swagger UI: http://localhost:8084/swagger-ui/index.html
- API Docs (JSON): http://localhost:8084/api-docs
- OpenAPI 3.0 Docs: http://localhost:8084/v3/api-docs

## 참고사항
- IDE에서 `io.swagger` import 오류가 표시되더라도 빌드는 정상 작동
- Gradle 동기화 또는 IDE 재시작으로 해결 가능
- 테스트 실행 시 gRPC 포트 충돌 방지: `./gradlew build -x test`

## 수정 파일
- `PlanIt-Insight-svc/build.gradle`
- `PlanIt-Insight-svc/src/main/java/com/planit/config/SecurityConfig.java`
- `PlanIt-Insight-svc/src/main/java/com/planit/config/RestTemplateConfig.java`

---
**작성일**: 2026-03-09
**상태**: ✅ 완료
