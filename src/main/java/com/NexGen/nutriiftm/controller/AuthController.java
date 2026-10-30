package com.NexGen.nutriiftm.controller;

import com.NexGen.nutriiftm.model.Usuario;
import com.NexGen.nutriiftm.repository.UsuarioRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Ponte entre o Firebase Authentication (que roda no navegador) e a sessão
 * do Spring Boot (que protege as páginas Thymeleaf do sistema).
 *
 * Fluxo:
 *  1. O navegador loga no Firebase (email/senha ou Google) e recebe um
 *     ID token (JWT).
 *  2. O navegador manda esse token pra cá, em POST /auth/sessao.
 *  3. Aqui a gente verifica o token com o Firebase Admin SDK — só cria
 *     sessão se o token for legítimo e não estiver expirado.
 *  4. Guardamos o uid/email na HttpSession. As demais páginas do sistema
 *     passam a confiar nessa sessão (via um interceptor/filtro),
 *     sem precisar validar token do Firebase de novo a cada clique.
 *  5. Também espelhamos o usuário na tabela local `usuario` (criando na
 *     primeira vez, atualizando o último login nas seguintes). O Firebase
 *     continua sendo o dono da autenticação — isso aqui é só um cadastro
 *     local para o resto do sistema poder referenciar "o usuário logado".
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;

    @PostMapping("/sessao")
    public ResponseEntity<Void> criarSessao(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build();
        }
        String token = authHeader.substring("Bearer ".length());

        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);

            Usuario usuario = sincronizarUsuarioLocal(decoded);

            HttpSession session = request.getSession(true); // cria se não existir
            request.changeSessionId();
            session.removeAttribute("csrfToken");
            session.setAttribute("uid", decoded.getUid());
            session.setAttribute("email", decoded.getEmail());
            session.setAttribute("nome", decoded.getName());
            session.setAttribute("admin", usuario.isAdmin());

            log.info("Sessão criada para usuário {}", decoded.getEmail());
            return ResponseEntity.ok()
                    .header("X-Admin", Boolean.toString(usuario.isAdmin()))
                    .build();

        } catch (FirebaseAuthException e) {
            log.warn("Token do Firebase inválido/expirado: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }

    /**
     * Cria o registro local na primeira vez que o uid aparece, ou apenas
     * atualiza nome/email/último login se o usuário já existir. Contas
     * novas nascem com admin = false e só precisam ser promovidas para
     * acessar o painel administrativo.
     */
    private Usuario sincronizarUsuarioLocal(FirebaseToken decoded) {
        LocalDateTime agora = LocalDateTime.now();

        Usuario usuario = usuarioRepository.findByFirebaseUid(decoded.getUid())
                .orElseGet(() -> {
                    Usuario novo = new Usuario();
                    novo.setFirebaseUid(decoded.getUid());
                    novo.setCriadoEm(agora);
                    novo.setAdmin(false);
                    return novo;
                });

        usuario.setEmail(decoded.getEmail());
        usuario.setNome(decoded.getName());
        if ((usuario.getFotoUrl() == null || usuario.getFotoUrl().isBlank())
                && decoded.getPicture() != null && !decoded.getPicture().isBlank()) {
            usuario.setFotoUrl(decoded.getPicture());
        }
        usuario.setUltimoLogin(agora);

        return usuarioRepository.save(usuario);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok().build();
    }
}