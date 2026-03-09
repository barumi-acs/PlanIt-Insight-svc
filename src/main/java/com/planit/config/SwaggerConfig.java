/**
 * Swagger/OpenAPI 설정
 * API 문서 자동 생성 및 JWT 인증 지원
 * 
 * @since 2026-03-03
 * @updated 2026-03-09 (JWT Bearer 토큰 인증 추가)
 */
package com.planit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    
    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";
    
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("PlanIt Insight Service API")
                .description("사용자의 할 일 처리 데이터를 분석하고 AI 기반 피드백을 제공하는 서비스")
                .version("v1.0.0")
                .contact(new Contact()
                    .name("PlanIt Team")
                    .email("support@planit.com")
                )
            )
            .servers(List.of(
                new Server()
                    .url("http://localhost:8084")
                    .description("로컬 개발 서버"),
                new Server()
                    .url("https://api.planit.com")
                    .description("운영 서버")
            ))
            // JWT Bearer 토큰 인증 설정
            .components(new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                    .name(SECURITY_SCHEME_NAME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT 토큰을 입력하세요 (Bearer 접두사 제외)")
                )
            )
            // 모든 API에 기본적으로 JWT 인증 적용
            .addSecurityItem(new SecurityRequirement()
                .addList(SECURITY_SCHEME_NAME)
            );
    }
}
