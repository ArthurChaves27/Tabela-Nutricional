package com.NexGen.nutriiftm.security;

import com.NexGen.nutriiftm.model.TabelaNutricional;
import com.NexGen.nutriiftm.model.Usuario;
import com.NexGen.nutriiftm.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RotuloAuthorizationService {

    private final UsuarioRepository usuarioRepository;

    public void exigirPermissaoDeEdicao(TabelaNutricional tabela, HttpServletRequest request) {
        if (tabela.getUsuario() == null) {
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("uid") instanceof String uid)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Usuario usuario = usuarioRepository.findByFirebaseUid(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!usuario.isAdmin()
                && !tabela.getUsuario().getUsuCodigo().equals(usuario.getUsuCodigo())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
