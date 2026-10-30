package com.NexGen.nutriiftm.controller;

import com.NexGen.nutriiftm.config.FirebaseWebConfig.FirebaseWeb;
import com.NexGen.nutriiftm.config.CsrfTokenService;
import com.NexGen.nutriiftm.model.Usuario;
import com.NexGen.nutriiftm.repository.UsuarioRepository;
import com.NexGen.nutriiftm.service.TabelaNutricionalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class PerfilController {

    private static final DateTimeFormatter DATA_MEMBRO_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UsuarioRepository usuarioRepository;
    private final TabelaNutricionalService tabelaNutricionalService;
    private final FirebaseWeb firebaseWeb;
    private final CsrfTokenService csrfTokenService;

    @GetMapping("/perfil")
    @Transactional(readOnly = true)
    public String meuPerfil(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int pagina,
            Model model) {
        Usuario usuario = usuarioDaSessao(request);
        if (usuario == null) {
            return "redirect:/login";
        }
        preencherPerfil(usuario, usuario, model, request, pagina);
        return "perfil";
    }

    @GetMapping("/perfil/{id:\\d+}")
    @Transactional(readOnly = true)
    public String perfilPublico(@PathVariable Long id, HttpServletRequest request, Model model) {
        Usuario usuarioPerfil = usuarioRepository.findProfileById(id).orElse(null);
        if (usuarioPerfil == null) {
            return "redirect:/perfil";
        }
        Usuario usuarioLogado = usuarioDaSessao(request);
        if (usuarioLogado == null) {
            return "redirect:/login";
        }
        preencherPerfil(usuarioPerfil, usuarioLogado, model, request, 0);
        return "perfil";
    }

    @GetMapping({"/perfil/salvar", "/perfil/foto"})
    public void getEmRotaPost() {
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @PostMapping("/perfil/salvar")
    @Transactional
    public String salvarPerfil(
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String estado,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        Usuario usuario = usuarioDaSessao(request);
        if (usuario == null) {
            return "redirect:/login";
        }

        String cidadeLimpa = cidade == null ? "" : cidade.trim();
        String estadoLimpo = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (cidadeLimpa.length() > 100 || estadoLimpo.length() > 2
                || (!estadoLimpo.isEmpty() && !estadoLimpo.matches("[A-Z]{2}"))
                || cidadeLimpa.isEmpty() != estadoLimpo.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "erroPerfil", "Informe cidade e UF juntas (ou deixe os dois campos vazios).");
            return "redirect:/perfil#editar";
        }

        usuario.setCidade(cidadeLimpa.isEmpty() ? null : cidadeLimpa);
        usuario.setEstado(estadoLimpo.isEmpty() ? null : estadoLimpo);
        usuarioRepository.save(usuario);
        redirectAttributes.addFlashAttribute("sucessoPerfil", "Perfil atualizado.");
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/foto")
    @Transactional
    public ResponseEntity<Void> salvarFoto(
            @RequestBody FotoPerfilRequest body,
            HttpServletRequest request) {

        Usuario usuario = usuarioDaSessao(request);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (body == null || !fotoPertenceAoUsuario(body.photoUrl(), usuario.getFirebaseUid())) {
            return ResponseEntity.badRequest().build();
        }

        usuario.setFotoUrl(body.photoUrl());
        usuarioRepository.save(usuario);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/perfil/seguir/{id}")
    @Transactional
    public String seguir(@PathVariable Long id, HttpServletRequest request) {
        Usuario usuario = usuarioDaSessao(request);
        Usuario seguido = usuarioRepository.findById(id).orElse(null);
        if (usuario == null || seguido == null || usuario.getUsuCodigo().equals(id)) {
            return "redirect:/perfil";
        }

        boolean jaSegue = usuario.getSeguindo().stream()
                .anyMatch(pessoa -> pessoa.getUsuCodigo().equals(id));
        if (!jaSegue) {
            usuario.getSeguindo().add(seguido);
            usuarioRepository.save(usuario);
        }
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/deixar-de-seguir/{id}")
    @Transactional
    public String deixarDeSeguir(@PathVariable Long id, HttpServletRequest request) {
        Usuario usuario = usuarioDaSessao(request);
        if (usuario == null) {
            return "redirect:/login";
        }
        usuario.getSeguindo().removeIf(pessoa -> pessoa.getUsuCodigo().equals(id));
        usuarioRepository.save(usuario);
        return "redirect:/perfil";
    }

    private void preencherPerfil(
            Usuario usuarioPerfil,
            Usuario usuarioLogado,
            Model model,
            HttpServletRequest request,
            int pagina) {
        var rotulos = tabelaNutricionalService.listarPorUsuario(usuarioPerfil.getUsuCodigo());
        boolean proprioPerfil = usuarioLogado != null
                && usuarioPerfil.getUsuCodigo().equals(usuarioLogado.getUsuCodigo());
        Set<Long> seguindoIds = usuarioLogado == null
                ? Set.of()
                : usuarioLogado.getSeguindo().stream()
                        .map(Usuario::getUsuCodigo)
                        .collect(Collectors.toCollection(HashSet::new));

        model.addAttribute("usuarioPerfil", usuarioPerfil);
        model.addAttribute("proprioPerfil", proprioPerfil);
        model.addAttribute("rotulos", rotulos);
        model.addAttribute("seguindo", usuarioPerfil.getSeguindo());
        model.addAttribute("seguidores", usuarioPerfil.getSeguidores());
        model.addAttribute("seguindoIds", seguindoIds);
        Page<Usuario> pessoasPage = proprioPerfil
                ? usuarioRepository.findAllByUsuCodigoNotOrderByNomeAsc(
                        usuarioPerfil.getUsuCodigo(), PageRequest.of(Math.max(0, pagina), 20))
                : Page.empty();
        model.addAttribute("pessoasParaSeguir", pessoasPage.getContent());
        model.addAttribute("paginaPessoas", pessoasPage);
        model.addAttribute("totalRotulos", rotulos.size());
        model.addAttribute("totalSeguindo", usuarioPerfil.getSeguindo().size());
        model.addAttribute("totalSeguidores", usuarioPerfil.getSeguidores().size());
        model.addAttribute("membroDesde", usuarioPerfil.getCriadoEm() == null
                ? ""
                : usuarioPerfil.getCriadoEm().toLocalDate().format(DATA_MEMBRO_FORMAT));
        model.addAttribute("firebaseWeb", firebaseWeb);
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("A sessão autenticada deveria estar disponível no perfil.");
        }
        model.addAttribute("csrfToken", csrfTokenService.tokenFor(session));
    }

    private Usuario usuarioDaSessao(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("uid") instanceof String uid)) {
            return null;
        }
        return usuarioRepository.findProfileByFirebaseUid(uid).orElse(null);
    }

    private boolean fotoPertenceAoUsuario(String photoUrl, String uid) {
        String bucket = firebaseWeb.getStorageBucket();
        if (photoUrl == null || bucket == null || bucket.isBlank() || uid == null) {
            return false;
        }
        try {
            URI uri = URI.create(photoUrl);
            String prefixoObjeto = "/v0/b/" + bucket + "/o/";
            if (!"https".equals(uri.getScheme())
                    || !"firebasestorage.googleapis.com".equals(uri.getHost())
                    || uri.getRawPath() == null
                    || !uri.getRawPath().startsWith(prefixoObjeto)
                    || uri.getRawQuery() == null
                    || !Pattern.compile("(?:^|&)token=[^&]+(?:&|$)").matcher(uri.getRawQuery()).find()) {
                return false;
            }

            String objeto = URLDecoder.decode(
                    uri.getRawPath().substring(prefixoObjeto.length()), StandardCharsets.UTF_8);
            String padraoObjeto = "^profile-photos/" + Pattern.quote(uid)
                    + "/avatar-[0-9]+\\.(?:jpg|png|webp)$";
            return objeto.matches(padraoObjeto);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public record FotoPerfilRequest(String photoUrl) {
    }
}
