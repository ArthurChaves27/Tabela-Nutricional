package com.NexGen.nutriiftm.service;

import com.NexGen.nutriiftm.model.TabelaNutricional; // ajuste para o tipo real
import com.NexGen.nutriiftm.model.Usuario;
import com.NexGen.nutriiftm.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private static final int TAMANHO_PAGINA = 20;
    private static final DateTimeFormatter DATA_MEMBRO_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UsuarioRepository usuarioRepository;
    private final TabelaNutricionalService tabelaNutricionalService;
    private final FotoStorageService fotoStorageService;

    /** Tudo que a página de perfil precisa, já calculado dentro da transação. */
    public record PerfilView(
            Usuario usuarioPerfil,
            boolean proprioPerfil,
            List<TabelaNutricional> rotulos,
            Set<Long> seguindoIds,
            Page<Usuario> pessoasPage,
            int totalRotulos,
            int totalSeguindo,
            int totalSeguidores,
            String membroDesde) {
    }

    /**
     * Carrega o perfil para exibição.
     * @param idPerfil id do perfil a mostrar; null significa "o meu próprio perfil"
     * @param uidLogado uid do Firebase de quem está logado
     * @return vazio se o perfil ou o usuário logado não existirem
     */
    @Transactional(readOnly = true)
    public Optional<PerfilView> carregarPerfil(Long idPerfil, String uidLogado, int pagina) {
        Optional<Usuario> logadoOpt = usuarioRepository.findProfileByFirebaseUid(uidLogado);
        if (logadoOpt.isEmpty()) {
            return Optional.empty();
        }
        Usuario logado = logadoOpt.get();

        Usuario perfil = idPerfil == null
                ? logado
                : usuarioRepository.findProfileById(idPerfil).orElse(null);
        if (perfil == null) {
            return Optional.empty();
        }

        boolean proprio = perfil.getUsuCodigo().equals(logado.getUsuCodigo());
        var rotulos = tabelaNutricionalService.listarPorUsuario(perfil.getUsuCodigo());

        Set<Long> seguindoIds = logado.getSeguindo().stream()
                .map(Usuario::getUsuCodigo)
                .collect(Collectors.toCollection(HashSet::new));

        Page<Usuario> pessoas = proprio
                ? usuarioRepository.findAllByUsuCodigoNotOrderByNomeAsc(
                        perfil.getUsuCodigo(), PageRequest.of(Math.max(0, pagina), TAMANHO_PAGINA))
                : Page.empty();

        String membroDesde = perfil.getCriadoEm() == null
                ? ""
                : perfil.getCriadoEm().toLocalDate().format(DATA_MEMBRO_FORMAT);

        return Optional.of(new PerfilView(
                perfil, proprio, rotulos, seguindoIds, pessoas,
                rotulos.size(),
                perfil.getSeguindo().size(),     // inicializa as coleções dentro da transação
                perfil.getSeguidores().size(),
                membroDesde));
    }

    /**
     * @throws IllegalArgumentException com mensagem pronta para o usuário se os dados forem inválidos
     * @throws java.util.NoSuchElementException se o usuário logado não existir no banco
     */
    @Transactional
    public void atualizarLocalizacao(String uid, String cidade, String estado) {
        Usuario usuario = usuarioRepository.findProfileByFirebaseUid(uid).orElseThrow();

        String cidadeLimpa = cidade == null ? "" : cidade.trim();
        String estadoLimpo = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (cidadeLimpa.length() > 100 || estadoLimpo.length() > 2
                || (!estadoLimpo.isEmpty() && !estadoLimpo.matches("[A-Z]{2}"))
                || cidadeLimpa.isEmpty() != estadoLimpo.isEmpty()) {
            throw new IllegalArgumentException("Informe cidade e UF juntas (ou deixe os dois campos vazios).");
        }

        usuario.setCidade(cidadeLimpa.isEmpty() ? null : cidadeLimpa);
        usuario.setEstado(estadoLimpo.isEmpty() ? null : estadoLimpo);
        usuarioRepository.save(usuario);
    }

    /** Salva a nova foto, grava a URL e apaga a anterior. Devolve a nova URL. */
    @Transactional
    public String atualizarFoto(String uid, MultipartFile foto) throws IOException {
        Usuario usuario = usuarioRepository.findProfileByFirebaseUid(uid).orElseThrow();

        String novaUrl = fotoStorageService.salvarAvatar(foto);
        String urlAntiga = usuario.getFotoUrl();

        usuario.setFotoUrl(novaUrl);
        usuarioRepository.save(usuario);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() {
            fotoStorageService.remover(urlAntiga);      // só apaga a antiga se o banco confirmou
        }
        @Override public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
                fotoStorageService.remover(novaUrl);    // se falhou, descarta a nova
            }
        }
    });       
    return novaUrl;
    }

    @Transactional
    public void seguir(String uid, Long idSeguido) {
        Usuario usuario = usuarioRepository.findProfileByFirebaseUid(uid).orElse(null);
        Usuario seguido = usuarioRepository.findById(idSeguido).orElse(null);
        if (usuario == null || seguido == null || usuario.getUsuCodigo().equals(idSeguido)) {
            return;
        }
        boolean jaSegue = usuario.getSeguindo().stream()
                .anyMatch(p -> p.getUsuCodigo().equals(idSeguido));
        if (!jaSegue) {
            usuario.getSeguindo().add(seguido);
            usuarioRepository.save(usuario);
        }
    }

    @Transactional
    public void deixarDeSeguir(String uid, Long idSeguido) {
        usuarioRepository.findProfileByFirebaseUid(uid).ifPresent(usuario -> {
            usuario.getSeguindo().removeIf(p -> p.getUsuCodigo().equals(idSeguido));
            usuarioRepository.save(usuario);
        });
    }
}
