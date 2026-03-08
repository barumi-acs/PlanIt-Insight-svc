package com.planit.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 챗봇 질의 요청 DTO
 * 
 * 프론트엔드에서 전달받는 챗봇 질의 요청 데이터
 * 
 * [Phase 1] 현재: userId는 백엔드에서 더미 값 사용
 * [Phase 2] 예정: userId는 JWT 토큰에서 추출
 * 
 * @since 2026-03-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequestDto {
    
    /**
     * 사용자 질의 내용 (필수)
     */
    @NotBlank(message = "질의 내용은 필수입니다")
    private String query;
}
