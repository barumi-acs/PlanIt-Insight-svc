package com.planit.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 챗봇 질의 응답 DTO
 * 
 * 프론트엔드로 반환하는 챗봇 응답 데이터
 * 
 * @since 2026-03-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotResponseDto {
    
    /**
     * AI 생성 답변
     */
    private String answer;
    
    /**
     * 데이터 출처 목록
     */
    private List<String> sources;
    
    /**
     * 응답 생성 시각 (ISO 8601 형식)
     */
    private String generatedAt;
}
