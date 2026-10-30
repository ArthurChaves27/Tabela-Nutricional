package com.NexGen.nutriiftm.config;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.servlet.HandlerMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor();

    @Test
    void administradorPodeAcessarRotaAdminExataMesmoComContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/nutri");
        request.setRequestURI("/nutri/admin");
        request.setSession(session(true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void usuarioComumNaoPodeAcessarRotaAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin;x=1/dashboard");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/admin/**");
        request.setSession(session(false));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals("/perfil", response.getRedirectedUrl());
    }

    private HttpSession session(boolean admin) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("uid", "firebase-uid");
        session.setAttribute("admin", admin);
        return session;
    }
}
