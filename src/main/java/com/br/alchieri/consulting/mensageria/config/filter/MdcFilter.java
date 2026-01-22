package com.br.alchieri.consulting.mensageria.config.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Component
@Order(1) // Executa bem cedo na cadeia de filtros
public class MdcFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id"; // Opcional: permite passar ID externo
    private static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        String traceId = null;
        if (servletRequest instanceof HttpServletRequest) {
             traceId = ((HttpServletRequest) servletRequest).getHeader(TRACE_ID_HEADER);
        }

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().substring(0, 8); // Gera um ID curto
        }

        // Coloca no MDC
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        try {
            // Continua a cadeia de filtros
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            // Limpa o MDC para a thread atual ao final da requisição
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
