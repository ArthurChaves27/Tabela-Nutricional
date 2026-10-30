package com.NexGen.nutriiftm.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfInterceptorTest {

    private final CsrfTokenService tokenService = new CsrfTokenService();
    private final CsrfInterceptor interceptor = new CsrfInterceptor(tokenService);

    @Test
    void postSemTokenRetornaForbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void postComTokenValidoEGetNaoExigemFalha() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = tokenService.tokenFor(session);
        MockHttpServletRequest post = new MockHttpServletRequest();
        post.setMethod("POST");
        post.setSession(session);
        post.addHeader("X-CSRF-TOKEN", token);

        assertTrue(interceptor.preHandle(post, new MockHttpServletResponse(), new Object()));

        MockHttpServletRequest get = new MockHttpServletRequest();
        get.setMethod("GET");
        assertTrue(interceptor.preHandle(get, new MockHttpServletResponse(), new Object()));
    }
}
