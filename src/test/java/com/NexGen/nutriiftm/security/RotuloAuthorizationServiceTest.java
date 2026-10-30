package com.NexGen.nutriiftm.security;

import com.NexGen.nutriiftm.model.TabelaNutricional;
import com.NexGen.nutriiftm.model.Usuario;
import com.NexGen.nutriiftm.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RotuloAuthorizationServiceTest {

    @Test
    void apenasDonoOuAdministradorPodeEditarRotuloComDono() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        Usuario dono = usuario(1L, "dono", false);
        Usuario visitante = usuario(2L, "visitante", false);
        Usuario admin = usuario(3L, "admin", true);
        when(repository.findByFirebaseUid("dono")).thenReturn(Optional.of(dono));
        when(repository.findByFirebaseUid("visitante")).thenReturn(Optional.of(visitante));
        when(repository.findByFirebaseUid("admin")).thenReturn(Optional.of(admin));

        TabelaNutricional tabela = new TabelaNutricional();
        tabela.setUsuario(dono);
        RotuloAuthorizationService authorization = new RotuloAuthorizationService(repository);

        assertDoesNotThrow(() -> authorization.exigirPermissaoDeEdicao(tabela, request("dono")));
        assertDoesNotThrow(() -> authorization.exigirPermissaoDeEdicao(tabela, request("admin")));
        ResponseStatusException erro = assertThrows(
                ResponseStatusException.class,
                () -> authorization.exigirPermissaoDeEdicao(tabela, request("visitante")));
        assertEquals(403, erro.getStatusCode().value());
    }

    @Test
    void rotuloSemDonoPreservaFluxoLegado() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        RotuloAuthorizationService authorization = new RotuloAuthorizationService(repository);
        TabelaNutricional tabelaLegada = new TabelaNutricional();

        assertDoesNotThrow(() -> authorization.exigirPermissaoDeEdicao(
                tabelaLegada, new MockHttpServletRequest()));
    }

    private Usuario usuario(Long id, String uid, boolean admin) {
        Usuario usuario = new Usuario();
        usuario.setUsuCodigo(id);
        usuario.setFirebaseUid(uid);
        usuario.setAdmin(admin);
        return usuario;
    }

    private MockHttpServletRequest request(String uid) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("uid", uid);
        request.setSession(session);
        return request;
    }
}
