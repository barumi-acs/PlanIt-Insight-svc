/**
 * Report gRPC Client
 * InsightAI-svc(Python)의 AI 리포트 생성 서비스를 gRPC로 호출
 * 
 * 장애 격리 원칙:
 * - Python AI 서버가 죽어있거나 에러를 뱉더라도 Java 서버의 메인 로직이 터지면 안 됨
 * - 적절한 try-catch와 Fallback 처리 포함
 * 
 * @since 2026-03-08
 */
package com.planit.analytics.grpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planit.analytics.dto.AIReportRequest;
import com.planit.analytics.dto.AIReportResponse;
import com.planit.analytics.port.AIReportPort;
import com.planit.grpc.report.GenerateReportRequest;
import com.planit.grpc.report.GenerateReportResponse;
import com.planit.grpc.report.ReportServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGrpcClient implements AIReportPort {
    
    @GrpcClient("report-service")
    private ReportServiceGrpc.ReportServiceBlockingStub reportServiceStub;
    
    private final ObjectMapper objectMapper;
    
    @Override
    public AIReportResponse generateReport(AIReportRequest request) {
        log.info("Calling InsightAI-svc via gRPC for user: {}, reportType: {}", 
                request.getUserId(), request.getReportType());
        
        try {
            // statisticsData를 JSON 문자열로 변환
            String statisticsDataJson = objectMapper.writeValueAsString(request.getStatisticsData());
            
            // gRPC 요청 생성
            GenerateReportRequest grpcRequest = GenerateReportRequest.newBuilder()
                    .setUserId(request.getUserId())
                    .setReportType(request.getReportType())
                    .setTargetPeriod(request.getTargetPeriod())
                    .setStatisticsData(statisticsDataJson)
                    .build();
            
            log.debug("gRPC request: userId={}, reportType={}, targetPeriod={}, statisticsData={}", 
                    request.getUserId(), request.getReportType(), request.getTargetPeriod(), statisticsDataJson);
            
            // gRPC 호출 (타임아웃 30초)
            GenerateReportResponse grpcResponse = reportServiceStub
                    .withDeadlineAfter(30, TimeUnit.SECONDS)
                    .generateReport(grpcRequest);
            
            log.info("gRPC response received: success={}, generatedAt={}", 
                    grpcResponse.getSuccess(), grpcResponse.getGeneratedAt());
            
            // JSON 문자열을 Map으로 파싱
            Map<String, Object> reportData = new HashMap<>();
            if (grpcResponse.getSuccess() && !grpcResponse.getReportData().isEmpty()) {
                try {
                    reportData = objectMapper.readValue(
                            grpcResponse.getReportData(), 
                            objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class)
                    );
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse report data JSON: {}", e.getMessage());
                    reportData = new HashMap<>();
                }
            }
            
            // AIReportResponse 생성
            return AIReportResponse.builder()
                    .success(grpcResponse.getSuccess())
                    .reportData(reportData)
                    .errorMessage(grpcResponse.getErrorMessage())
                    .build();
            
        } catch (StatusRuntimeException e) {
            // gRPC 통신 에러 (서버 다운, 타임아웃 등)
            log.error("gRPC call failed for user: {}, status: {}, description: {}", 
                    request.getUserId(), e.getStatus().getCode(), e.getStatus().getDescription(), e);
            
            return createFallbackResponse(
                    "InsightAI 서비스 통신 실패: " + e.getStatus().getDescription()
            );
            
        } catch (JsonProcessingException e) {
            // JSON 변환 에러
            log.error("Failed to serialize statistics data for user: {}", request.getUserId(), e);
            
            return createFallbackResponse(
                    "통계 데이터 변환 실패: " + e.getMessage()
            );
            
        } catch (Exception e) {
            // 기타 예상치 못한 에러
            log.error("Unexpected error while calling InsightAI-svc for user: {}", 
                    request.getUserId(), e);
            
            return createFallbackResponse(
                    "AI 리포트 생성 중 예상치 못한 오류 발생: " + e.getMessage()
            );
        }
    }
    
    /**
     * Fallback 응답 생성
     * Python AI 서버 장애 시에도 Java 서버가 정상 동작하도록 보장
     * 
     * @param errorMessage 에러 메시지
     * @return Fallback AIReportResponse
     */
    private AIReportResponse createFallbackResponse(String errorMessage) {
        log.warn("Creating fallback response: {}", errorMessage);
        
        return AIReportResponse.builder()
                .success(false)
                .reportData(new HashMap<>())
                .errorMessage(errorMessage)
                .build();
    }
}
