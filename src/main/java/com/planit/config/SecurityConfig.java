package com.planit.config;

import com.planit.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정
 * 
 * [Phase 2] JWT 기반 인증/인가 적용 완료
 * 
 * @since 2026-03-08
 * @updated 2026-03-09 (JWT 인증 적용)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Security Filter Chain 설정
     * 
     * [Phase 2 적용 완료]
     * - JWT 필터 추가
     * - 챗봇/피드백 API는 authenticated() 적용
     * - 배치 API는 permitAll() 유지 (추후 관리자 권한 추가 예정)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS 설정 활성화
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // CSRF 비활성화 (REST API는 CSRF 토큰 불필요)
            .csrf(csrf -> csrf.disable())
            
            // 세션 정책: Stateless (JWT 사용 시 세션 불필요)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 엔드포인트별 인증/인가 설정
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
                
                // ✅ [Phase 2] JWT 인증 필요
                .requestMatchers("/api/v1/insight/chat/**").authenticated()
                
                // ✅ [Phase 2] JWT 인증 필요
                .requestMatchers("/api/v1/feedbacks/**").authenticated()
                
                // TODO: [Phase 3] 배치 API는 관리자 권한 필요 (hasRole("ADMIN"))
                // 현재는 테스트를 위해 인증 우회
                .requestMatchers("/api/v1/batch/**").permitAll()
                
                // 내부 API (서비스 간 통신) - 인증 우회
                .requestMatchers("/internal/**").permitAll()
                
                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            )
            // ✅ 미인증 요청 시 403이 아닌 401 반환 (FE 인터셉터가 401을 감지해 로그인 페이지로 이동)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            // ✅ JWT 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 설정 (JWT 인증 적용)
     * 
     * [설정 내용]
     * - allowedOrigins: application.yml에서 환경별로 관리
     * - allowedMethods: GET, POST, PUT, DELETE, OPTIONS, PATCH 허용
     * - allowedHeaders: 모든 헤더 허용 (Authorization 포함)
     * - allowCredentials: true (JWT 토큰 포함 요청 허용)
     * - maxAge: Preflight 요청 캐싱 시간 (1시간)
     */
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
        
        // 노출할 헤더 (프론트엔드에서 접근 가능한 헤더)
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-User-Id"
        ));
        
        // Credentials 허용 (JWT 토큰 포함 요청 허용)
        configuration.setAllowCredentials(true);
        
        // Preflight 요청 캐싱 시간 (초 단위)
        configuration.setMaxAge(3600L);
        
        // 모든 경로에 CORS 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
