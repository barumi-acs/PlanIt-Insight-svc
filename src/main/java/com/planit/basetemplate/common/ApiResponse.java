/** 
 * [planit 글로벌 룰 - 공통 응답 규격]
 * 모든 응답은 공통 응답 규격을 따릅니다.
 * @since 2026-02-23
 */

package com.planit.basetemplate.common;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ApiResponse<T> {
    private String code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code("C2001")
                .message("성공")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}