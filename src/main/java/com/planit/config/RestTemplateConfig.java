/**
 * RestTemplate 설정
 * Service B 호출을 위한 HTTP 클라이언트 설정
 * @since 2026-03-03
 */
package com.planit.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    
    /**
     * RestTemplate 빈 생성
     * - 연결 타임아웃: 5초
     * - 읽기 타임아웃: 10초 (AI 처리 시간 고려)
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofSeconds(5))
            .withReadTimeout(Duration.ofSeconds(10));
        
        return builder
            .requestFactory(() -> ClientHttpRequestFactories.get(settings))
            .build();
    }
}
