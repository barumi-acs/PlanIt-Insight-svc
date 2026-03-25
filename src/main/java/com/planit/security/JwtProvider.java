package com.planit.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 검증 Provider (Insight-svc 전용)
 * 토큰 발급은 User-svc에서만 수행. 이 서비스는 검증만 담당.
 * User-svc와 동일한 JWT_SECRET 사용 필수.
 * 
 * @since 2026-03-09
 * @updated 2026-03-20 (MDC userId 추가)
 */
@Component
public class JwtProvider {
    
    private final SecretKey secretKey;
    
    public JwtProvider(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
            throw new RuntimeException("JWT_EXPIRED_TOKEN");
        } catch (SignatureException | MalformedJwtException e) {
            throw new RuntimeException("JWT_INVALID_TOKEN");
        } catch (Exception e) {
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
            throw new RuntimeException("JWT_EXPIRED_TOKEN");
        } catch (SignatureException | MalformedJwtException e) {
            throw new RuntimeException("JWT_INVALID_TOKEN");
        } catch (Exception e) {
            throw new RuntimeException("JWT_INVALID_TOKEN");
        }
    }
}
