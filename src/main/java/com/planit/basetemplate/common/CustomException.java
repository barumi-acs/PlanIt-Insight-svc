/** 
 * [planit 글로벌 룰 - 공통 예외 처리]
 * 서비스의 모든 예외는 이 클래스를 통하여 처리됩니다.
 * 던져진 예외는 GlobalExceptionHandler에서 가로채어 공통 응답 규격으로 변환됩니다.
 * 사용 예시: throw new CustomException(ErrorCode.C4041);
 * @since 2026-02-23
 */

package com.planit.basetemplate.common;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}