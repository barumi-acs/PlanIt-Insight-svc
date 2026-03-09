package com.planit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 인증 필터
 * 
 * [역할]
 * - 모든 HTTP 요청에서 Authorization 헤더 검증
 * - JWT 토큰에서 userId 추출 후 SecurityContext에 저장
 * - Controller에서 @AuthenticationPrincipal로 userId 자동 주입 가능
 * 
 * [인증 제외 경로]
 * - Swagger UI, API docs
 * - 헬스체크, 테스트 엔드포인트
 * - 내부 API (서비스 간 통신)
 * 
 * @since 2026-03-09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        
        // 인증 제외 경로는 필터 통과
        if (shouldNotFilter(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractTokenFromRequest(request);

            if (token != null && jwtProvider.validateToken(token)) {
                String userId = jwtProvider.getUserIdFromToken(token);

                // SecurityContext에 인증 정보 저장
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,  // principal (Controller에서 @AuthenticationPrincipal로 주입됨)
                                null,    // credentials (비밀번호 불필요)
                                Collections.emptyList()  // authorities (권한 목록)
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                log.debug("[JWT] Authenticated user: {}", userId);
            }
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            // 인증 실패 시에도 필터 체인 계속 진행 (SecurityConfig에서 401 처리)
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 인증 제외 경로 확인
     */
    private boolean shouldNotFilter(String path) {
        return path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/swagger-resources") ||
               path.startsWith("/webjars") ||
               path.startsWith("/api/v1/base") ||
               path.startsWith("/sample") ||
               path.startsWith("/api/v1/insight/actuator") ||
               path.startsWith("/internal");
    }

    /**
     * Authorization 헤더에서 JWT 토큰 추출
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
