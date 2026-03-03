/** 
 * [planit 글로벌 룰 - 메인 애플리케이션]
 * 이 클래스는 planit 글로벌 룰을 적용한 메인 애플리케이션 클래스입니다.
 * Spring Boot 애플리케이션의 시작점으로 사용됩니다.
 * @since 2026-02-26
 */
package com.planit.basetemplate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing // 시간 자동화
@SpringBootApplication
public class PlanitBaseTemplateApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlanitBaseTemplateApplication.class, args);
	}

}
