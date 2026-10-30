package com.NexGen.nutriiftm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CsrfInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final CsrfTokenService csrfTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (SAFE_METHODS.contains(request.getMethod()) || csrfTokenService.isValid(request)) {
            return true;
        }

        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token CSRF ausente ou inválido.");
        return false;
    }
}
