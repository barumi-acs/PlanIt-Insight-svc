package com.planit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정
 * 
 * [Phase 1] 현재: 테스트 단계 - 챗봇 API 인증 우회
 * [Phase 2] 예정: JWT 기반 인증/인가 적용
 * 
 * @since 2026-03-08
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Security Filter Chain 설정
     * 
     * [현재 상태]
     * - 챗봇 API는 permitAll()로 인증 우회 (테스트용)
     * - CSRF 비활성화 (REST API)
     * - Stateless 세션 정책
     * 
     * [Phase 2 TODO]
     * - JWT 필터 추가
     * - permitAll() 제거 및 authenticated() 적용
     * - 역할 기반 접근 제어 (RBAC) 추가
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
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                
                // 헬스체크 및 테스트 엔드포인트 허용
                .requestMatchers(
                    "/api/v1/base/**",
                    "/sample/**"
                ).permitAll()
                
                // TODO: [Phase 2] 프론트엔드 로그인(JWT) 연동 시 permitAll() 제거 및 인증 필터 적용
                // 챗봇 API - 현재는 테스트를 위해 인증 우회
                .requestMatchers("/api/v1/insight/chat/**").permitAll()
                
                // TODO: [Phase 2] 프론트엔드 로그인(JWT) 연동 시 permitAll() 제거 및 인증 필터 적용
                // 피드백 API - 현재는 테스트를 위해 인증 우회
                .requestMatchers("/api/v1/feedbacks/**").permitAll()
                
                // TODO: [Phase 2] 배치 API는 관리자 권한 필요 (hasRole("ADMIN"))
                // 배치 API - 현재는 테스트를 위해 인증 우회
                .requestMatchers("/api/v1/batch/**").permitAll()
                
                // 내부 API (서비스 간 통신) - 인증 우회
                .requestMatchers("/internal/**").permitAll()
                
                // 그 외 모든 요청은 인증 필요
                .anyRequest().authenticated()
            );

        return http.build();
    }

    /**
     * CORS 설정 (영구 적용 - JWT 대비)
     * 
     * [설정 내용]
     * - allowedOrigins: application.yml에서 환경별로 관리
     * - allowedMethods: GET, POST, PUT, DELETE, OPTIONS 허용
     * - allowedHeaders: 모든 헤더 허용 (Authorization 포함)
     * - allowCredentials: true (JWT 토큰 포함 요청 허용)
     * - maxAge: Preflight 요청 캐싱 시간 (1시간)
     * 
     * [Phase 2 TODO]
     * - 프로덕션 환경에서는 application-prod.yml에 실제 도메인 설정
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
        // 🌟 Phase 2 JWT 연동 시 필수 설정
        configuration.setAllowCredentials(true);
        
        // Preflight 요청 캐싱 시간 (초 단위)
        configuration.setMaxAge(3600L);
        
        // 모든 경로에 CORS 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
