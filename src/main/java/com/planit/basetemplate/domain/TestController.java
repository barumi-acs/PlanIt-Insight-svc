/**
 * [planit 글로벌 룰 - 테스트 컨트롤러]
 * 서비스 실행 테스트 및 예시를 보여줍니다.
 * 삭제하셔도 됩니다.
 * @since 2026-02-23
 */

package com.planit.basetemplate.domain;

import com.planit.basetemplate.common.ApiResponse;
import com.planit.basetemplate.common.CustomException;
import com.planit.basetemplate.common.ErrorCode;

import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/base") // 형님의 라우팅 규칙 적용
public class TestController {

    @GetMapping("/test")
    public ApiResponse<String> test() {
        log.info("[PlanIt Base Template] 테스트 API 호출");
        return ApiResponse.success("PlanIt base 템플릿");
    }

    @GetMapping("/error-test")
    public ApiResponse<Void> errorTest() {
        throw new CustomException(ErrorCode.C4041);
    }
}