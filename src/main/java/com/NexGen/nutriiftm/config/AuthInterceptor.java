package com.NexGen.nutriiftm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * Exige uma sessão válida nas rotas protegidas e mantém o painel
 * administrativo exclusivo para administradores.
 *
 * Não confunda com autenticação em si — este interceptor NÃO valida
 * nada do Firebase, apenas confere a sessão marcada como logada e o papel
 * de administrador nas rotas /admin/**. A validação do token acontece em
 * POST /auth/sessao.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        HttpSession session = request.getSession(false); // não cria sessão nova aqui
        boolean logado = session != null && session.getAttribute("uid") != null;

        if (logado) {
            Object bestMatch = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String path = bestMatch == null
                    ? request.getRequestURI().substring(request.getContextPath().length())
                    : bestMatch.toString();
            boolean rotaAdmin = path.equals("/admin") || path.startsWith("/admin/");
            if (rotaAdmin && !Boolean.TRUE.equals(session.getAttribute("admin"))) {
                response.sendRedirect("/perfil");
                return false;
            }
            return true;
        }

        response.sendRedirect("/login");
        return false;
    }
}