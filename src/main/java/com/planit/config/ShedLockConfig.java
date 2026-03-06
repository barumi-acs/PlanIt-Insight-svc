/**
 * ShedLock 설정
 * 분산 환경에서 스케줄러 중복 실행 방지
 * @since 2026-03-03
 */
package com.planit.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "1h")
public class ShedLockConfig {
    
    /**
     * JDBC 기반 Lock Provider 생성
     * shedlock 테이블을 사용하여 분산 락 관리
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime() // DB 시간 기준 사용
            .build()
        );
    }
}
