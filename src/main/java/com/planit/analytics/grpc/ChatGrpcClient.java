package com.planit.analytics.grpc;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

/**
 * 챗봇 gRPC 클라이언트
 * 
 * InsightAI-svc (Python)의 ChatbotService와 통신하여
 * 사용자 질의에 대한 AI 답변을 받아옵니다.
 * 
 * [통신 구조]
 * FE → Insight-svc (Java BFF) → InsightAI-svc (Python gRPC)
 * 
 * [장애 격리]
 * - Python AI 서버 다운 시 Fallback 응답 반환
 * - 타임아웃 설정으로 무한 대기 방지
 * 
 * @since 2026-03-08
 */
@Slf4j
@Service
public class ChatGrpcClient {

    /**
     * gRPC Stub 주입
     * application.yml의 grpc.client.chat-service 설정 사용
     */
    @GrpcClient("chat-service")
    private ChatbotServiceGrpc.ChatbotServiceBlockingStub chatbotStub;

    /**
     * 챗봇 질의 처리
     * 
     * @param userId 사용자 ID
     * @param query 사용자 질의
     * @return ChatResponse AI 생성 답변
     */
    public ChatResponse queryChatbot(String userId, String query) {
        log.info("[ChatGrpc] Querying chatbot: user={}, query={}", userId, query);
        
        try {
            // gRPC 요청 생성
            ChatRequest request = ChatRequest.newBuilder()
                    .setUserId(userId)
                    .setQuery(query)
                    .build();
            
            // gRPC 호출
            ChatResponse response = chatbotStub.queryChatbot(request);
            
            log.info("[ChatGrpc] Response received: answer_length={}, sources={}",
                    response.getAnswer().length(),
                    response.getSourcesCount());
            
            return response;
            
        } catch (StatusRuntimeException e) {
            log.error("[ChatGrpc] gRPC call failed: status={}, description={}",
                    e.getStatus().getCode(),
                    e.getStatus().getDescription(),
                    e);
            
            // 장애 격리: Fallback 응답 반환
            return createFallbackResponse(userId, query);
        } catch (Exception e) {
            log.error("[ChatGrpc] Unexpected error during gRPC call", e);
            return createFallbackResponse(userId, query);
        }
    }

    /**
     * Fallback 응답 생성
     * 
     * Python AI 서버가 다운되었거나 에러 발생 시
     * 기본 응답을 반환하여 서비스 중단을 방지합니다.
     * 
     * @param userId 사용자 ID
     * @param query 사용자 질의
     * @return ChatResponse Fallback 응답
     */
    private ChatResponse createFallbackResponse(String userId, String query) {
        log.warn("[ChatGrpc] Returning fallback response for user={}", userId);
        
        return ChatResponse.newBuilder()
                .setAnswer("죄송합니다. 일시적으로 AI 챗봇 서비스에 접속할 수 없습니다. 잠시 후 다시 시도해주세요.")
                .addSources("Fallback")
                .setGeneratedAt(java.time.Instant.now().toString())
                .build();
    }
}
