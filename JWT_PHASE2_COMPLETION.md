# JWT 인증 통합 완료 (Phase 2)

## 작업 완료 일시
2026-03-09

## 작업 내용

### 1. JWT 관련 클래스 추가
- ✅ `JwtProvider.java`: JWT 토큰 검증 및 userId 추출
- ✅ `JwtAuthenticationFilter.java`: 모든 HTTP 요청에서 JWT 토큰 검증

### 2. 의존성 추가 (build.gradle)
```gradle
// JWT 라이브러리 (Phase 2 JWT 인증)
implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'
```

### 3. JWT 설정 추가 (application.yml)
```yaml
jwt:
  secret: ${JWT_SECRET:your-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-please-change-this-in-production}
  access-token-validity: ${JWT_ACCESS_TOKEN_VALIDITY:900000}    # 15분
  refresh-token-validity: ${JWT_REFRESH_TOKEN_VALIDITY:604800000}  # 7일
```

### 4. SecurityConfig 수정
- ✅ `JwtAuthenticationFilter` 주입 및 필터 체인에 추가
- ✅ 챗봇 API: `permitAll()` → `authenticated()` 변경
- ✅ 피드백 API: `permitAll()` → `authenticated()` 변경
- ⏳ 배치 API: `permitAll()` 유지 (Phase 3에서 관리자 권한 추가 예정)

### 5. SwaggerConfig 수정
- ✅ JWT Bearer 토큰 인증 스키마 추가
- ✅ Swagger UI에서 "Authorize" 버튼으로 토큰 입력 가능

### 6. Controller 수정
#### ChatbotController
- ✅ 더미 userId 제거
- ✅ `@AuthenticationPrincipal String userId` 파라미터 추가

#### FeedbackController
- ✅ 더미 userId 제거 (2곳)
- ✅ `@AuthenticationPrincipal String userId` 파라미터 추가 (2곳)
  - `getDailyCheer()`
  - `getDashboard()`

---

## Swagger UI 사용 방법

### 1. Swagger UI 접속
```
http://localhost:8084/swagger-ui/index.html
```

### 2. JWT 토큰 입력
1. 우측 상단 "Authorize" 버튼 클릭
2. "Bearer Authentication" 입력창에 JWT 토큰 입력 (Bearer 접두사 제외)
3. "Authorize" 버튼 클릭
4. "Close" 버튼으로 닫기

### 3. API 테스트
- 이제 모든 API 요청에 자동으로 `Authorization: Bearer {token}` 헤더가 추가됩니다.
- 🔒 자물쇠 아이콘이 표시된 API는 JWT 인증이 필요합니다.

---

## 테스트 방법

### 1. User-svc에서 JWT 토큰 발급
```bash
curl -X POST http://localhost:8080/api/v1/users/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "cognitoIdToken": "eyJraWQiOiJ..."
  }'
```

**응답 예시:**
```json
{
  "success": true,
  "data": {
    "userId": "01234567-89ab-cdef-0123-456789abcdef",
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

### 2. Insight-svc 챗봇 API 호출 (JWT 포함)
```bash
curl -X POST http://localhost:8084/api/v1/insight/chat/query \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  -d '{
    "query": "이번 주 가장 많이 완료한 카테고리는?"
  }'
```

**성공 응답:**
```json
{
  "success": true,
  "data": {
    "answer": "이번 주 '운동' 카테고리를 가장 많이 완료했어요.",
    "sources": ["action_log_2026_03"],
    "generatedAt": "2026-03-09T12:00:00Z"
  }
}
```

**토큰 없이 호출 시 (401 Unauthorized):**
```json
{
  "success": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다"
  }
}
```

### 3. Swagger UI에서 테스트
1. User-svc에서 로그인하여 accessToken 복사
2. Insight-svc Swagger UI 접속
3. "Authorize" 버튼 클릭 → 토큰 입력
4. 챗봇 API 또는 피드백 API 테스트

---

## 주의사항

### 1. JWT Secret Key 관리
- **User-svc와 Insight-svc는 동일한 Secret Key 사용 필수**
- 환경 변수로 관리: `JWT_SECRET`
- 최소 256비트 (32자 이상)

### 2. 토큰 만료 처리
- Access Token: 15분 (짧게 유지)
- Refresh Token: 7일 (User-svc에서만 관리)
- Insight-svc는 Access Token만 검증

### 3. CORS 설정
- `allowCredentials(true)` 필수 (JWT 토큰 포함 요청 허용)
- Frontend Origin을 `cors.allowed-origins`에 추가

### 4. 인증 제외 경로
- Swagger UI, API docs
- 헬스체크, 테스트 엔드포인트
- 내부 API (서비스 간 통신)
- 배치 API (Phase 3에서 관리자 권한 추가 예정)

---

## Frontend 연동 (다음 단계)

### 1. 로그인 후 토큰 저장
```typescript
// src/api/auth.service.ts
const { accessToken, refreshToken } = response.data.data;
localStorage.setItem('accessToken', accessToken);
localStorage.setItem('refreshToken', refreshToken);
```

### 2. API 요청 시 토큰 자동 포함
```typescript
// src/api/index.ts
apiClient.interceptors.request.use((config) => {
  const accessToken = localStorage.getItem('accessToken');
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});
```

### 3. 401 에러 시 로그아웃 처리
```typescript
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

---

## 변경된 파일 목록

### 신규 파일
- `src/main/java/com/planit/security/JwtProvider.java`
- `src/main/java/com/planit/security/JwtAuthenticationFilter.java`
- `JWT_PHASE2_COMPLETION.md` (이 문서)

### 수정된 파일
- `build.gradle` (JWT 의존성 추가)
- `src/main/resources/application.yml` (JWT 설정 추가)
- `src/main/java/com/planit/config/SecurityConfig.java` (JWT 필터 추가, 인증 적용)
- `src/main/java/com/planit/config/SwaggerConfig.java` (JWT Bearer 토큰 스키마 추가)
- `src/main/java/com/planit/analytics/controller/ChatbotController.java` (더미 userId 제거)
- `src/main/java/com/planit/analytics/controller/FeedbackController.java` (더미 userId 제거)
- `JWT_INTEGRATION_ANALYSIS.md` (완료 상태로 업데이트)

---

## 다음 단계 (Phase 3)

- [ ] 배치 API에 관리자 권한 추가 (`hasRole("ADMIN")`)
- [ ] Frontend JWT 토큰 저장 및 자동 포함 로직 구현
- [ ] 통합 테스트 (로그인 → 토큰 발급 → Insight API 호출)
- [ ] 토큰 갱신 로직 구현 (Refresh Token)
- [ ] 에러 메시지 한글화 및 표준화

---

**작성자**: Kiro AI Assistant  
**작성일**: 2026-03-09  
**상태**: ✅ Backend JWT 인증 통합 완료
