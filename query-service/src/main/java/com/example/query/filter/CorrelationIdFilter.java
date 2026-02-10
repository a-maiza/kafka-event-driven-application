package com.example.query.filter;

import com.example.common.CorrelationIdUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    private static final String HEADER_NAME = "X-Correlation-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdUtils.generate();
        }

        CorrelationIdUtils.setInMdc(correlationId);
        httpResponse.setHeader(HEADER_NAME, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            CorrelationIdUtils.clearMdc();
        }
    }
}
