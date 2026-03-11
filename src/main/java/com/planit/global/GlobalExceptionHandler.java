/** 
 * [planit 글로벌 룰 - 예외 처리 변환]
 * 서비스의 모든 예외는 이 클래스에서 공통 응답 규격으로 전환됩니다.
 * @since 2026-02-23
 */
package com.planit.global;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 파라미터 검증 실패 예외 처리 (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation error: {}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .code(ErrorCode.C4001.getCode())
                        .message(errorMessage)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    // ErrorCode로 정의된 예외 처리
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        HttpStatus status = resolveHttpStatus(e.getErrorCode());
        log.error("Custom exception: code={}, message={}", e.getErrorCode().getCode(), e.getErrorCode().getMessage());
        return ResponseEntity.status(status)
                .body(ApiResponse.<Void>builder()
                        .code(e.getErrorCode().getCode())
                        .message(e.getErrorCode().getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    // 그 외 모든 예외 처리 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllException(Exception e) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder()
                        .code(ErrorCode.C5001.getCode())
                        .message(ErrorCode.C5001.getMessage())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    private HttpStatus resolveHttpStatus(ErrorCode code) {
        return switch (code) {
            case C4001, AI4001 -> HttpStatus.BAD_REQUEST;
            case C4011, C4012 -> HttpStatus.UNAUTHORIZED;
            case C4031, S4031 -> HttpStatus.FORBIDDEN;
            case C4041, IS4041 -> HttpStatus.NOT_FOUND;
            case C4051 -> HttpStatus.METHOD_NOT_ALLOWED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
