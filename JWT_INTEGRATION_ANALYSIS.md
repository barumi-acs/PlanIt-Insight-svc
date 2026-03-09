# JWT 인증 통합 분석 및 적용 가이드

## 1. User-svc JWT 토큰 흐름 분석

### 1.1 로그인/회원가입 시 토큰 발급
```
[Frontend] → [User-svc]
POST /api/v1/users/auth/login
Body: { cognitoIdToken: "..." }

↓

[AuthService.login()]
1. Cognito ID Token 검증 (CognitoService)
2. cognito_sub로 사용자 조회
3. JWT 토큰 발급:
   - accessToken = jwtProvider.createAccessToken(userId)
   - refreshToken = jwtProvider.createRefreshToken(userId)
4. Refresh Token을 Redis에 저장 (7일)

↓

[Response]
{
  "userId": "uuid-v7",
  "nickname": "사용자닉네임",
  "email": "user@example.com",
  "accessToken": "eyJhbGc...",  // 15분 유효
  "refreshToken": "eyJhbGc..."  // 7일 유효
}
```

### 1.2 JWT 토큰 구조
```java
// JwtProvider.java
public String createAccessToken(String userId) {
    return Jwts.builder()
        .setSubject(userId)           // userId를 subject에 저장
        .setIssuedAt(now)
        .setExpiration(validity)      // 15분 후 만료
        .signWith(secretKey, HS256)
        .compact();
}
```

**토큰 Payload:**
```json
{
  "sub": "01234567-89ab-cdef-0123-456789abcdef",  // userId (UUID v7)
  "iat": 1709971200,  // 발급 시각
  "exp": 1709972100   // 만료 시각 (15분 후)
}
```

### 1.3 API 요청 시 JWT 인증 흐름
```
[Frontend] → [User-svc]
GET /api/v1/users/profile
Headers: {
  Authorization: "Bearer eyJhbGc..."
}

↓

[JwtAuthenticationFilter.doFilterInternal()]
1. Authorization 헤더에서 토큰 추출
   - "Bearer " 제거 → 순수 JWT 토큰

2. 토큰 검증 (jwtProvider.validateToken())
   - 서명 검증
   - 만료 시간 확인
   - 형식 검증

3. userId 추출 (jwtProvider.getUserIdFromToken())
   - JWT payload의 subject 필드 → userId

4. SecurityContext에 인증 정보 저장
   UsernamePasswordAuthenticationToken authentication = 
       new UsernamePasswordAuthenticationToken(
           userId,              // principal (인증된 사용자 ID)
           null,                // credentials (비밀번호 불필요)
           Collections.emptyList()  // authorities (권한 목록)
       );
   SecurityContextHolder.getContext().setAuthentication(authentication);

↓

[Controller]
@DeleteMapping("/withdraw")
public ApiResponse<Void> withdraw(
    @AuthenticationPrincipal String userId  // SecurityContext에서 자동 주입
) {
    authService.withdraw(userId);
    return ApiResponse.success(...);
}
```

### 1.4 인증 제외 경로 (User-svc)
```java
// JwtAuthenticationFilter.shouldNotFilter()
- /swagger-ui/**
- /v3/api-docs/**
- /api/v1/users/auth/login
- /api/v1/users/auth/signup
- /api/v1/users/auth/check-withdrawn
- /api/v1/users/categories
- /api/v1/users/terms
- /actuator/**
```

---

## 2. Insight-svc JWT 통합 적용 방안

### 2.1 현재 상태 (Phase 1)
```java
// SecurityConfig.java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/insight/chat/**").permitAll()  // ❌ 인증 우회
    .requestMatchers("/api/v1/feedbacks/**").permitAll()     // ❌ 인증 우회
    .requestMatchers("/api/v1/batch/**").permitAll()         // ❌ 인증 우회
)

// ChatbotController.java
String userId = "test-user-001";  // ❌ 더미 userId
```

### 2.2 적용 단계

#### Step 1: User-svc의 JWT 관련 클래스 복사
```
PlanIt-User-svc/src/main/java/com/planit/userservice/security/
├── JwtProvider.java              → PlanIt-Insight-svc로 복사
└── JwtAuthenticationFilter.java  → PlanIt-Insight-svc로 복사
```

#### Step 2: build.gradle에 JWT 의존성 추가
```gradle
// JWT 라이브러리
implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'
```

#### Step 3: application.yml에 JWT 설정 추가
```yaml
jwt:
  secret: ${JWT_SECRET:your-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm}
  access-token-validity: ${JWT_ACCESS_TOKEN_VALIDITY:900000}    # 15분 (밀리초)
  refresh-token-validity: ${JWT_REFRESH_TOKEN_VALIDITY:604800000}  # 7일 (밀리초)
```

#### Step 4: SecurityConfig 수정
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Swagger UI 및 API 문서 접근 허용
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                
                // 헬스체크 및 테스트 엔드포인트 허용
                .requestMatchers(
                    "/api/v1/base/**",
                    "/sample/**",
                    "/api/v1/insight/actuator/**"
                ).permitAll()
                
                // ✅ Phase 2: JWT 인증 필요
                .requestMatchers("/api/v1/insight/chat/**").authenticated()
                .requestMatchers("/api/v1/feedbacks/**").authenticated()
                
                // 배치 API는 관리자 권한 필요 (추후 구현)
                .requestMatchers("/api/v1/batch/**").hasRole("ADMIN")
                
                // 내부 API (서비스 간 통신) - 인증 우회
                .requestMatchers("/internal/**").permitAll()
                
                .anyRequest().authenticated()
            )
            // JWT 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

#### Step 5: Controller에서 userId 추출
```java
// ChatbotController.java (수정 전)
public ResponseEntity<ApiResponse<ChatbotResponseDto>> queryChatbot(
    @Valid @RequestBody ChatbotRequestDto request
) {
    String userId = "test-user-001";  // ❌ 더미 userId
    ...
}

// ChatbotController.java (수정 후)
public ResponseEntity<ApiResponse<ChatbotResponseDto>> queryChatbot(
    @AuthenticationPrincipal String userId,  // ✅ JWT에서 자동 추출
    @Valid @RequestBody ChatbotRequestDto request
) {
    log.info("[ChatbotController] Received query: user={}, query={}",
            userId, request.getQuery());
    ...
}
```

#### Step 6: FeedbackController 수정
```java
// FeedbackController.java (수정 전)
public ResponseEntity<ApiResponse<FeedbackDashboard>> getFeedbackDashboard(
    @RequestParam String targetPeriod
) {
    String userId = "test-user-001";  // ❌ 더미 userId
    ...
}

// FeedbackController.java (수정 후)
public ResponseEntity<ApiResponse<FeedbackDashboard>> getFeedbackDashboard(
    @AuthenticationPrincipal String userId,  // ✅ JWT에서 자동 추출
    @RequestParam String targetPeriod
) {
    log.info("[FeedbackController] Get dashboard: user={}, period={}",
            userId, targetPeriod);
    ...
}
```

---

## 3. Frontend 연동 방법

### 3.1 로그인 후 토큰 저장
```typescript
// src/api/auth.service.ts
export const login = async (cognitoIdToken: string) => {
  const response = await axios.post('/api/v1/users/auth/login', {
    cognitoIdToken
  });
  
  const { accessToken, refreshToken } = response.data.data;
  
  // 토큰 저장 (localStorage 또는 secure cookie)
  localStorage.setItem('accessToken', accessToken);
  localStorage.setItem('refreshToken', refreshToken);
  
  return response.data;
};
```

### 3.2 API 요청 시 토큰 자동 포함
```typescript
// src/api/index.ts
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
});

// Request Interceptor: 모든 요청에 JWT 토큰 자동 추가
apiClient.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem('accessToken');
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: 401 에러 시 토큰 갱신 또는 로그아웃
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      // 토큰 만료 → 로그아웃 처리
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

---

## 4. 테스트 방법

### 4.1 로그인 후 토큰 발급 테스트
```bash
# 1. User-svc 로그인
curl -X POST http://localhost:8080/api/v1/users/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "cognitoIdToken": "eyJraWQiOiJ..."
  }'

# Response:
{
  "success": true,
  "data": {
    "userId": "01234567-89ab-cdef-0123-456789abcdef",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

### 4.2 Insight-svc API 호출 테스트
```bash
# 2. Insight-svc 챗봇 API 호출 (JWT 포함)
curl -X POST http://localhost:8084/api/v1/insight/chat/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{
    "query": "이번 주 가장 많이 완료한 카테고리는?"
  }'

# 성공 시:
{
  "success": true,
  "data": {
    "answer": "이번 주 '운동' 카테고리를 가장 많이 완료했어요.",
    "sources": ["action_log_2026_03"],
    "generatedAt": "2026-03-09T12:00:00Z"
  }
}

# 토큰 없이 호출 시:
{
  "success": false,
  "error": {
    "code": "C4001",
    "message": "인증이 필요합니다"
  }
}
```

---

## 5. 주의사항

### 5.1 JWT Secret Key 관리
- **절대 하드코딩 금지**: application.yml에 환경 변수로 관리
- **최소 256비트**: HS256 알고리즘 요구사항
- **서비스별 동일한 Secret Key 사용**: User-svc와 Insight-svc가 같은 키 공유

### 5.2 토큰 만료 처리
- Access Token: 15분 (짧게 유지)
- Refresh Token: 7일 (User-svc에서만 관리)
- Insight-svc는 Access Token만 검증 (Refresh Token 갱신은 User-svc에서)

### 5.3 CORS 설정
- `allowCredentials(true)` 필수 (JWT 토큰 포함 요청 허용)
- Frontend Origin을 `cors.allowed-origins`에 추가

---

## 6. 마이그레이션 체크리스트

- [x] User-svc의 `JwtProvider.java` 복사
- [x] User-svc의 `JwtAuthenticationFilter.java` 복사
- [x] `build.gradle`에 JWT 의존성 추가
- [x] `application.yml`에 JWT 설정 추가
- [x] `SecurityConfig.java` 수정 (permitAll → authenticated)
- [x] `ChatbotController.java`에서 `@AuthenticationPrincipal` 사용
- [x] `FeedbackController.java`에서 `@AuthenticationPrincipal` 사용
- [ ] Frontend에서 로그인 후 토큰 저장 로직 구현
- [ ] Frontend API 클라이언트에 Authorization 헤더 자동 추가
- [ ] 통합 테스트 (로그인 → 토큰 발급 → Insight API 호출)

---

**작성일**: 2026-03-09
**상태**: ✅ Backend 구현 완료 (Frontend 연동 대기)
