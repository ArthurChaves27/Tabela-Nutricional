package com.NexGen.nutriiftm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CsrfTokenService {

    public static final String SESSION_ATTRIBUTE = "csrfToken";
    private static final String REQUEST_PARAMETER = "_csrf";
    private static final String REQUEST_HEADER = "X-CSRF-TOKEN";
    private final SecureRandom secureRandom = new SecureRandom();

    public String tokenFor(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ATTRIBUTE);
        if (existing instanceof String token) {
            return token;
        }

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_ATTRIBUTE, token);
        return token;
    }

    public boolean isValid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SESSION_ATTRIBUTE) instanceof String expected)) {
            return false;
        }

        String supplied = request.getHeader(REQUEST_HEADER);
        if (supplied == null || supplied.isBlank()) {
            supplied = request.getParameter(REQUEST_PARAMETER);
        }
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
