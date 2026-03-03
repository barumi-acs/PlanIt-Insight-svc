/**
 * Swagger/OpenAPI 설정
 * API 문서 자동 생성
 * @since 2026-03-03
 */
package com.planit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    
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
                    .url("http://localhost:8080")
                    .description("로컬 개발 서버"),
                new Server()
                    .url("https://api.planit.com")
                    .description("운영 서버")
            ));
    }
}
