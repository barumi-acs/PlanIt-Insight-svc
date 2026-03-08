# Security Phase 1 Setup - 챗봇 API 테스트 환경 구성

## 개요

챗봇 API의 진입점을 Java(Insight-svc)로 변경하면서, Phase 2의 JWT 인증 연동을 대비한 Security 설정을 구성했습니다.

### Phase 구분
- **Phase 1 (현재)**: 테스트 단계 - 인증 우회, 더미 userId 사용
- **Phase 2 (예정)**: JWT 기반 인증/인가 적용

---

## 주요 변경사항

### 1. Spring Security 의존성 추가

**파일**: `build.gradle`

```gradle
// Spring Security (Phase 2 JWT 인증 대비)
implementation 'org.springframework.boot:spring-boot-starter-security'
testImplementation 'org.springframework.security:spring-security-test'
```

---

### 2. SecurityConfig 생성 (영구 적용)

**파일**: `src/main/java/com/planit/config/SecurityConfig.java`

#### 2.1 CORS 설정 (영구 적용 - JWT 대비 🌟)

```java
@Value("${cors.allowed-origins}")
private String[] allowedOrigins;

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    
    // 허용할 Origin (application.yml에서 주입)
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
    
    // 허용할 HTTP 메서드
    configuration.setAllowedMethods(Arrays.asList(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
    ));
    
    // 허용할 헤더 (모든 헤더 허용)
    configuration.setAllowedHeaders(List.of("*"));
    
    // 노출할 헤더
    configuration.setExposedHeaders(Arrays.asList(
        "Authorization",
        "Content-Type",
        "X-User-Id"
    ));
    
    // Credentials 허용 (JWT 토큰 포함 요청 허용)
    // 🌟 Phase 2 JWT 연동 시 필수 설정
    configuration.setAllowCredentials(true);
    
    // Preflight 요청 캐싱 시간 (1시간)
    configuration.setMaxAge(3600L);
    
    return source;
}
```

**application.yml 설정**:
```yaml
# CORS 설정 (Phase 2 JWT 대비)
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173,http://localhost:5174,http://127.0.0.1:3000,http://127.0.0.1:5173,http://127.0.0.1:5174}
```

**환경 변수로 오버라이드 가능**:
```bash
# 개발 환경
export CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173

# 프로덕션 환경
export CORS_ALLOWED_ORIGINS=https://planit.example.com,https://www.planit.example.com
```

**주요 설정**:
- `allowedOrigins`: application.yml에서 환경별로 관리
- `allowedMethods`: GET, POST, PUT, DELETE, OPTIONS, PATCH 허용
- `allowedHeaders`: 모든 헤더 허용 (Authorization 포함)
- `allowCredentials(true)`: JWT 토큰 포함 요청 허용 (Phase 2 필수)
- `maxAge`: Preflight 요청 캐싱 시간 (1시간)

#### 2.2 Security Filter Chain (임시 인증 우회)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> 
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authorizeHttpRequests(auth -> auth
            // Swagger UI 허용
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            
            // 헬스체크 허용
            .requestMatchers("/api/v1/base/**", "/sample/**").permitAll()
            
            // TODO: [Phase 2] 프론트엔드 로그인(JWT) 연동 시 permitAll() 제거 및 인증 필터 적용
            .requestMatchers("/api/v1/insight/chat/**").permitAll()
            .requestMatchers("/api/v1/feedbacks/**").permitAll()
            .requestMatchers("/api/v1/batch/**").permitAll()
            
            // 내부 API 허용
            .requestMatchers("/internal/**").permitAll()
            
            .anyRequest().authenticated()
        );

    return http.build();
}
```

**주요 설정**:
- CSRF 비활성화 (REST API)
- Stateless 세션 정책 (JWT 사용 시 세션 불필요)
- 챗봇/피드백/배치 API는 현재 `permitAll()` (테스트용)
- Phase 2에서 `authenticated()` 또는 `hasRole()` 적용 예정

---

### 3. ChatbotController 수정 (임시 더미 userId)

**파일**: `src/main/java/com/planit/analytics/controller/ChatbotController.java`

```java
@PostMapping("/query")
public ResponseEntity<ChatbotResponseDto> queryChatbot(
        @Valid @RequestBody ChatbotRequestDto request
) {
    // TODO: [Phase 2] @AuthenticationPrincipal 또는 SecurityContextHolder에서 실제 userId 추출로 변경
    // 현재는 테스트를 위해 더미 userId 사용
    String userId = "test-user-001";
    
    log.info("[ChatbotController] Received query: user={}, query={}",
            userId,
            request.getQuery());
    
    // gRPC 호출
    ChatResponse grpcResponse = chatGrpcClient.queryChatbot(
            userId,  // 더미 userId 전달
            request.getQuery()
    );
    
    // ... 응답 처리
}
```

**주요 변경**:
- `ChatbotRequestDto`에서 `userId` 필드 제거
- Controller에서 하드코딩된 더미 userId 사용 (`test-user-001`)
- Phase 2에서 JWT 토큰에서 userId 추출로 변경 예정

---

### 4. ChatbotRequestDto 수정

**파일**: `src/main/java/com/planit/analytics/dto/ChatbotRequestDto.java`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequestDto {
    
    /**
     * 사용자 질의 내용 (필수)
     */
    @NotBlank(message = "질의 내용은 필수입니다")
    private String query;
    
    // userId 필드 제거 (백엔드에서 처리)
}
```

---

## Phase 2 마이그레이션 가이드

### Phase 2에서 수정할 사항

#### 1. SecurityConfig - 인증 필터 적용

```java
// TODO 주석이 있는 부분 수정
.authorizeHttpRequests(auth -> auth
    // permitAll() 제거
    .requestMatchers("/api/v1/insight/chat/**").authenticated()
    .requestMatchers("/api/v1/feedbacks/**").authenticated()
    
    // 배치 API는 관리자 권한 필요
    .requestMatchers("/api/v1/batch/**").hasRole("ADMIN")
    
    .anyRequest().authenticated()
)
// JWT 필터 추가
.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
```

#### 2. ChatbotController - JWT에서 userId 추출

```java
@PostMapping("/query")
public ResponseEntity<ChatbotResponseDto> queryChatbot(
        @AuthenticationPrincipal UserDetails userDetails,  // JWT에서 추출
        @Valid @RequestBody ChatbotRequestDto request
) {
    String userId = userDetails.getUsername();  // 실제 userId
    
    // ... 기존 로직
}
```

또는 SecurityContextHolder 사용:

```java
String userId = SecurityContextHolder.getContext()
    .getAuthentication()
    .getName();
```

#### 3. JWT 필터 추가

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) {
        // JWT 토큰 추출
        String token = extractToken(request);
        
        // JWT 검증 및 인증 객체 생성
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication auth = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        
        filterChain.doFilter(request, response);
    }
}
```

#### 4. CORS allowedOrigins 프로덕션 도메인 추가

**application.yml 또는 환경 변수**:
```yaml
cors:
  allowed-origins: https://planit.example.com,https://www.planit.example.com
```

또는 환경 변수:
```bash
export CORS_ALLOWED_ORIGINS=https://planit.example.com,https://www.planit.example.com
```

---

## 테스트 방법

### 1. 빌드 및 실행

```bash
cd PlanIt-Insight-svc
./gradlew clean build
./gradlew bootRun
```

### 2. 챗봇 API 테스트

```bash
curl -X POST http://localhost:8084/api/v1/insight/chat/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "이번 주 가장 많이 완료한 카테고리는?"
  }'
```

**예상 응답**:
```json
{
  "answer": "이번 주 '운동' 카테고리를 가장 많이 완료했어요.",
  "sources": ["action_log_2026_02"],
  "generatedAt": "2026-03-08T15:30:00Z"
}
```

### 3. CORS 테스트

프론트엔드에서 API 호출:

```typescript
// PlanIt-FE/src/api/insight.service.ts
const response = await fetch('http://localhost:8084/api/v1/insight/chat/query', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  credentials: 'include',  // Phase 2 JWT 대비
  body: JSON.stringify({
    query: '이번 주 가장 많이 완료한 카테고리는?'
  })
});
```

---

## 주의사항

### Phase 1 (현재)
- 모든 사용자가 `test-user-001`로 처리됨
- 실제 프로덕션 환경에서는 사용 불가
- 테스트 및 E2E 통신 검증 목적으로만 사용

### Phase 2 (예정)
- JWT 토큰 필수
- 실제 userId 기반 데이터 조회
- 역할 기반 접근 제어 (RBAC)
- 프로덕션 도메인 CORS 설정

---

## TODO 체크리스트

### Phase 1 완료 항목
- [x] Spring Security 의존성 추가
- [x] CORS 설정 (allowCredentials: true)
- [x] Security Filter Chain 구성 (permitAll)
- [x] ChatbotController 더미 userId 적용
- [x] ChatbotRequestDto userId 필드 제거
- [x] TODO 주석 추가

### Phase 2 작업 항목
- [ ] JWT 필터 구현
- [ ] SecurityConfig permitAll() 제거
- [ ] ChatbotController JWT에서 userId 추출
- [ ] 역할 기반 접근 제어 (RBAC)
- [ ] 프로덕션 CORS 도메인 추가
- [ ] JWT 토큰 갱신 로직
- [ ] 인증 실패 핸들러

---

## 관련 문서

- [API_SPECIFICATION.md](./API_SPECIFICATION.md) - API 명세서
- [CHATBOT_BFF_MIGRATION.md](./CHATBOT_BFF_MIGRATION.md) - 챗봇 BFF 패턴 전환 가이드
- [ENVIRONMENT_VARIABLES.md](./ENVIRONMENT_VARIABLES.md) - 환경 변수 설정

---

## 버전 정보

- Phase: 1 (테스트 단계)
- Last Updated: 2026-03-08
- Next Phase: JWT 인증 연동 (예정)
