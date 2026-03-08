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
 * - 내부 gRPC 통신으로 Python AI 서버 호출
 * - 응답 변환 및 에러 처리
 * 
 * [보안]
 * - Java BFF를 통한 단일 진입점
 * - Python 서버는 외부 노출 차단
 * - 인증/인가 로직 중앙 집중화 가능
 * 
 * @since 2026-03-08
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
     * 2. gRPC 메시지로 변환
     * 3. Python AI 서버 호출 (gRPC)
     * 4. gRPC 응답을 JSON으로 변환
     * 5. 프론트엔드로 반환
     * 
     * @param request 챗봇 질의 요청
     * @return ChatbotResponseDto AI 생성 답변
     */
    @PostMapping("/query")
    @Operation(
            summary = "챗봇 질의",
            description = "사용자의 질의를 AI 챗봇에 전달하여 답변을 받습니다."
    )
    public ResponseEntity<ChatbotResponseDto> queryChatbot(
            @Valid @RequestBody ChatbotRequestDto request
    ) {
        log.info("[ChatbotController] Received query: user={}, query={}",
                request.getUserId(),
                request.getQuery());
        
        try {
            // gRPC 호출
            ChatResponse grpcResponse = chatGrpcClient.queryChatbot(
                    request.getUserId(),
                    request.getQuery()
            );
            
            // DTO 변환
            ChatbotResponseDto response = ChatbotResponseDto.builder()
                    .answer(grpcResponse.getAnswer())
                    .sources(grpcResponse.getSourcesList())
                    .generatedAt(grpcResponse.getGeneratedAt())
                    .build();
            
            log.info("[ChatbotController] Query completed: user={}, answer_length={}",
                    request.getUserId(),
                    response.getAnswer().length());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("[ChatbotController] Error processing query: user={}",
                    request.getUserId(), e);
            
            // 에러 발생 시에도 Fallback 응답 반환 (gRPC 클라이언트에서 처리됨)
            throw e;
        }
    }
}
