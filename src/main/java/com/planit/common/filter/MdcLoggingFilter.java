package com.planit.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * MDC(Mapped Diagnostic Context) 로깅 필터
 * 
 * 모든 HTTP 요청에 대해:
 * 1. traceId 생성/추출하여 MDC에 저장
 * 2. 요청 종료 시 MDC 정리 (메모리 누수 방지)
 * 
 * @since 2026-03-20
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter implements Filter {
    
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_KEY = "traceId";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        try {
            // Trace ID 추출 또는 생성
            String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString().replace("-", "");
            }
            
            // MDC에 traceId 저장
            MDC.put(TRACE_ID_KEY, traceId);
            
            // 다음 필터 체인 실행
            chain.doFilter(request, response);
            
        } finally {
            // 요청 종료 시 MDC 정리 (메모리 누수 방지)
            MDC.clear();
        }
    }
}
