package com.NexGen.nutriiftm.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfTokenServiceTest {

    private final CsrfTokenService csrfTokenService = new CsrfTokenService();

    @Test
    void tokenDaSessaoValidaHeaderEParametroDeFormulario() {
        MockHttpSession session = new MockHttpSession();
        String token = csrfTokenService.tokenFor(session);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setSession(session);
        request.addHeader("X-CSRF-TOKEN", token);
        assertTrue(csrfTokenService.isValid(request));

        MockHttpServletRequest formRequest = new MockHttpServletRequest();
        formRequest.setMethod("POST");
        formRequest.setSession(session);
        formRequest.setParameter("_csrf", token);
        assertTrue(csrfTokenService.isValid(formRequest));
    }

    @Test
    void rejeitaTokenAusenteOuDiferente() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setSession(new MockHttpSession());
        assertFalse(csrfTokenService.isValid(request));

        request.getSession().setAttribute(CsrfTokenService.SESSION_ATTRIBUTE, "token-esperado");
        request.setParameter("_csrf", "token-incorreto");
        assertFalse(csrfTokenService.isValid(request));
    }

    @Test
    void tokenEhAleatorioEPersistidoNaSessao() {
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();

        String first = csrfTokenService.tokenFor(firstSession);
        String repeated = csrfTokenService.tokenFor(firstSession);
        String second = csrfTokenService.tokenFor(secondSession);

        assertTrue(first.length() >= 40);
        assertEquals(first, repeated);
        assertNotEquals(first, second);
    }
}
