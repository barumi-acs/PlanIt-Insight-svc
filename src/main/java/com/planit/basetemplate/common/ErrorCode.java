/** 
 * [planit 글로벌 룰 - 예외 처리 리스트]
 * 서비스의 모든 예외는 이 클래스에서 정의됩니다.
 * U, S, AI, IS를 사용하여 원하는 예외를 정의합니다.
 * @since 2026-02-23
 */

package com.planit.basetemplate.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    C4001("C4001", "잘못된 요청 파라미터입니다."), // 파라미터 누락, 타입 불일치 등
    C4011("C4011", "인증 토큰이 만료되었습니다."), // JWT 만료
    C4012("C4012", "인증되지 않은 사용자입니다."), // 로그인 안함
    C4031("C4031", "접근 권한이 없습니다."), // 권한 부족
    C4041("C4041", "요청한 리소스를 찾을 수 없습니다."), // 잘못된 URL 등
    C4051("C4051", "허용되지 않은 메서드입니다."), // GET인데 POST로 보냄
    C5001("C5001", "서버 내부 에러가 발생했습니다."); // 그 외 모든 서버 에러

    private final String code;
    private final String message;
}