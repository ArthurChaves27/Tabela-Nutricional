package com.NexGen.nutriiftm.controller;

import com.NexGen.nutriiftm.config.CsrfTokenService;
import com.NexGen.nutriiftm.service.PerfilService;
import com.NexGen.nutriiftm.service.PerfilService.PerfilView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;

@Controller
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;
    private final CsrfTokenService csrfTokenService;

    @GetMapping("/perfil")
    public String meuPerfil(@RequestParam(defaultValue = "0") int pagina,
                            HttpServletRequest request, Model model) {
        String uid = uidDaSessao(request);
        if (uid == null) {
            return "redirect:/login";
        }
        return perfilService.carregarPerfil(null, uid, pagina)
                .map(view -> montarPagina(view, model, request))
                .orElse("redirect:/login");
    }

    @GetMapping("/perfil/{id:\\d+}")
    public String perfilPublico(@PathVariable Long id,
                                HttpServletRequest request, Model model) {
        String uid = uidDaSessao(request);
        if (uid == null) {
            return "redirect:/login";
        }
        return perfilService.carregarPerfil(id, uid, 0)
                .map(view -> montarPagina(view, model, request))
                .orElse("redirect:/perfil");
    }

    @GetMapping({"/perfil/salvar", "/perfil/foto"})
    public void getEmRotaPost() {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @PostMapping("/perfil/salvar")
    public String salvarPerfil(@RequestParam(required = false) String cidade,
                               @RequestParam(required = false) String estado,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        String uid = uidDaSessao(request);
        if (uid == null) {
            return "redirect:/login";
        }
        try {
            perfilService.atualizarLocalizacao(uid, cidade, estado);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erroPerfil", e.getMessage());
            return "redirect:/perfil#editar";
        } catch (NoSuchElementException e) {
            return "redirect:/login";
        }
        redirectAttributes.addFlashAttribute("sucessoPerfil", "Perfil atualizado.");
        return "redirect:/perfil";
    }

    @PostMapping(value = "/perfil/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> salvarFoto(@RequestParam("foto") MultipartFile foto,
                                                          HttpServletRequest request) {
        String uid = uidDaSessao(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            String url = perfilService.atualizarFoto(uid, foto);
            return ResponseEntity.ok(Map.of("photoUrl", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", "Não foi possível salvar a imagem no servidor."));
        }
    }

    @PostMapping("/perfil/seguir/{id}")
    public String seguir(@PathVariable Long id, HttpServletRequest request) {
        String uid = uidDaSessao(request);
        if (uid == null) {
            return "redirect:/login";
        }
        perfilService.seguir(uid, id);
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/deixar-de-seguir/{id}")
    public String deixarDeSeguir(@PathVariable Long id, HttpServletRequest request) {
        String uid = uidDaSessao(request);
        if (uid == null) {
            return "redirect:/login";
        }
        perfilService.deixarDeSeguir(uid, id);
        return "redirect:/perfil";
    }

    // ── auxiliares (só coisas de HTTP) ──

    private String uidDaSessao(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("uid") instanceof String uid) {
            return uid;
        }
        return null;
    }

    /** Copia os dados do service para o Model, com os mesmos nomes que o perfil.html já usa. */
    private String montarPagina(PerfilView v, Model model, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new IllegalStateException("A sessão autenticada deveria estar disponível no perfil.");
        }
        model.addAttribute("usuarioPerfil", v.usuarioPerfil());
        model.addAttribute("proprioPerfil", v.proprioPerfil());
        model.addAttribute("rotulos", v.rotulos());
        model.addAttribute("seguindo", v.usuarioPerfil().getSeguindo());
        model.addAttribute("seguidores", v.usuarioPerfil().getSeguidores());
        model.addAttribute("seguindoIds", v.seguindoIds());
        model.addAttribute("pessoasParaSeguir", v.pessoasPage().getContent());
        model.addAttribute("paginaPessoas", v.pessoasPage());
        model.addAttribute("totalRotulos", v.totalRotulos());
        model.addAttribute("totalSeguindo", v.totalSeguindo());
        model.addAttribute("totalSeguidores", v.totalSeguidores());
        model.addAttribute("membroDesde", v.membroDesde());
        model.addAttribute("csrfToken", csrfTokenService.tokenFor(session));
        return "perfil";
    }
}
