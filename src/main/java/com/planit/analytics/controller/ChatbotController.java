package com.planit.analytics.controller;

import com.planit.analytics.dto.ChatbotRequestDto;
import com.planit.analytics.dto.ChatbotResponseDto;
import com.planit.analytics.grpc.ChatGrpcClient;
import com.planit.analytics.grpc.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 챗봇 API 컨트롤러 (BFF 패턴)
 * 
 * [아키텍처]
 * FE → Insight-svc (Java BFF) → InsightAI-svc (Python gRPC)
 * 
 * [역할]
 * - 프론트엔드의 REST API 요청 수신
 * - JWT 토큰에서 userId 추출 (자동)
 * - 내부 gRPC 통신으로 Python AI 서버 호출
 * - 응답 변환 및 에러 처리
 * 
 * [보안]
 * - Java BFF를 통한 단일 진입점
 * - JWT 기반 인증/인가
 * - Python 서버는 외부 노출 차단
 * 
 * @since 2026-03-08
 * @updated 2026-03-09 (JWT 인증 적용)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/insight/chat")
@RequiredArgsConstructor
@Validated
@Tag(name = "Chatbot", description = "AI 챗봇 API")
public class ChatbotController {

    private final ChatGrpcClient chatGrpcClient;

    /**
     * 챗봇 질의 API
     * 
     * 사용자의 질의를 받아 AI 챗봇 답변을 반환합니다.
     * 
     * [처리 흐름]
     * 1. REST API 요청 수신 (JSON)
     * 2. JWT 토큰에서 userId 자동 추출 (@AuthenticationPrincipal)
     * 3. gRPC 메시지로 변환
     * 4. Python AI 서버 호출 (gRPC)
     * 5. gRPC 응답을 JSON으로 변환
     * 6. 프론트엔드로 반환
     * 
     * @param userId JWT 토큰에서 추출된 사용자 ID (자동 주입)
     * @param request 챗봇 질의 요청
     * @return ChatbotResponseDto AI 생성 답변
     */
    @PostMapping("/query")
    @Operation(
            summary = "챗봇 질의",
            description = "사용자의 질의를 AI 챗봇에 전달하여 답변을 받습니다."
    )
    public ResponseEntity<com.planit.global.ApiResponse<ChatbotResponseDto>> queryChatbot(
            @org.springframework.security.core.annotation.AuthenticationPrincipal String userId,
            @Valid @RequestBody ChatbotRequestDto request
    ) {
        log.info("[ChatbotController] Received query: user={}, query={}",
                userId,
                request.getQuery());
        
        try {
            // gRPC 호출
            ChatResponse grpcResponse = chatGrpcClient.queryChatbot(
                    userId,
                    request.getQuery()
            );
            
            // DTO 변환
            ChatbotResponseDto response = ChatbotResponseDto.builder()
                    .answer(grpcResponse.getAnswer())
                    .sources(grpcResponse.getSourcesList())
                    .generatedAt(grpcResponse.getGeneratedAt())
                    .build();
            
            log.info("[ChatbotController] Query completed: user={}, answer_length={}",
                    userId,
                    response.getAnswer().length());
            
            return ResponseEntity.ok(com.planit.global.ApiResponse.success(response));
            
        } catch (Exception e) {
            log.error("[ChatbotController] Error processing query: user={}",
                    userId, e);
            
            // 에러 발생 시에도 Fallback 응답 반환 (gRPC 클라이언트에서 처리됨)
            throw e;
        }
    }
}
