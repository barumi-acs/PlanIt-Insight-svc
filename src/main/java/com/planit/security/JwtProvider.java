package com.planit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 토큰 생성 및 검증 Provider
 * 
 * [역할]
 * - JWT 토큰 검증 (서명, 만료 시간, 형식)
 * - JWT 토큰에서 userId 추출
 * 
 * [주의사항]
 * - User-svc와 동일한 Secret Key 사용 필수
 * - Insight-svc는 토큰 발급 기능 사용 안 함 (User-svc에서만 발급)
 * 
 * @since 2026-03-09
 */
@Slf4j
@Component
public class JwtProvider {
    
    private final SecretKey secretKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;
    
    public JwtProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token-validity}") long accessTokenValidityMs,
        @Value("${jwt.refresh-token-validity}") long refreshTokenValidityMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }
    
    /**
     * JWT 토큰 검증
     * 
     * @param token JWT 토큰
     * @return 유효하면 true
     * @throws RuntimeException 토큰이 만료되었거나 유효하지 않은 경우
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("JWT token expired: {}", e.getMessage());
            throw new RuntimeException("JWT_EXPIRED_TOKEN");
        } catch (SignatureException | MalformedJwtException e) {
            log.error("JWT token invalid: {}", e.getMessage());
            throw new RuntimeException("JWT_INVALID_TOKEN");
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            throw new RuntimeException("JWT_INVALID_TOKEN");
        }
    }
    
    /**
     * JWT 토큰에서 userId 추출
     * 
     * @param token JWT 토큰
     * @return userId (UUID v7 문자열)
     */
    public String getUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            return claims.getSubject();
        } catch (ExpiredJwtException e) {
            log.error("JWT token expired: {}", e.getMessage());
            throw new RuntimeException("JWT_EXPIRED_TOKEN");
        } catch (SignatureException | MalformedJwtException e) {
            log.error("JWT token invalid: {}", e.getMessage());
            throw new RuntimeException("JWT_INVALID_TOKEN");
        } catch (Exception e) {
            log.error("JWT parsing failed: {}", e.getMessage());
            throw new RuntimeException("JWT_INVALID_TOKEN");
        }
    }
}
