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
 * @since 2026-03-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotRequestDto {
    
    /**
     * 사용자 ID (필수)
     */
    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;
    
    /**
     * 사용자 질의 내용 (필수)
     */
    @NotBlank(message = "질의 내용은 필수입니다")
    private String query;
}
