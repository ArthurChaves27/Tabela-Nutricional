package com.NexGen.nutriiftm.controller;

import com.NexGen.nutriiftm.model.TabelaNutricional;
import com.NexGen.nutriiftm.repository.UsuarioRepository;
import com.NexGen.nutriiftm.security.RotuloAuthorizationService;
import com.NexGen.nutriiftm.service.ElementoService;
import com.NexGen.nutriiftm.service.ProdutoService;
import com.NexGen.nutriiftm.service.TabelaNutricionalService;
import com.NexGen.nutriiftm.service.UnidadeMedidaService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/tabela")
@RequiredArgsConstructor
public class TabelaNutricionalController {

    private final TabelaNutricionalService tabelaService;
    private final ProdutoService produtoService;
    private final ElementoService elementoService;
    private final UnidadeMedidaService unidadeService;
    private final UsuarioRepository usuarioRepository;
    private final RotuloAuthorizationService rotuloAuthorizationService;

    // =========================
    // LISTAR
    // =========================

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("tabelas", tabelaService.listarTodos());
        return "tabela";
    }

    // =========================
    // NUTRIENTES
    // =========================

    @GetMapping("/nutrientes")
    public String nutrientes(Model model) {
        model.addAttribute("elementos", elementoService.listarTodos());
        return "nutrientes";
    }

    // =========================
    // INSERIR
    // =========================

    @GetMapping("/inserir")
    public String formInserir(Model model) {
        model.addAttribute("tabela", new TabelaNutricional());
        model.addAttribute("produtos", produtoService.listarTodos());
        model.addAttribute("unidades", unidadeService.listarTodos());
        model.addAttribute("elementos", elementoService.listarTodos());
        return "inserirTabela";
    }

    /**
     * Salva a TabelaNutricional (insert ou update).
     *
     * CORREÇÃO P-16: No fluxo de INSERT (tabCodigo ausente), redireciona para
     * /nutrientes/valores/inserir/{id} em vez de /tabela. Isso orienta o usuário
     * a adicionar os nutrientes imediatamente após criar a tabela, evitando
     * rótulos vazios sem nenhuma instrução visível.
     *
     * No fluxo de UPDATE (tabCodigo presente), redireciona para /tabela como antes.
     */
    @PostMapping("/salvar")
    public String salvar(

            @RequestParam(required = false)
            Long tabCodigo,

            @RequestParam Long produtoId,

            @RequestParam Long unidadeId,

            @RequestParam(required = false)
            Double tabValorEnergetico,

            @RequestParam(required = false)
            Double tabValorEnergeticoPorcao,

            @RequestParam(required = false)
            Double tabPorcao,

            @RequestParam(required = false)
            Double tabTotalPorcao,

            @RequestParam(required = false)
            Double tabTotalColheres,

            @RequestParam(required = false)
            Double tabVD,

            HttpServletRequest request
    ) {
        boolean isInsert = (tabCodigo == null);

        TabelaNutricional t = isInsert
                ? new TabelaNutricional()
                : tabelaService.buscarPorId(tabCodigo);
        if (!isInsert) {
            rotuloAuthorizationService.exigirPermissaoDeEdicao(t, request);
        }

        t.setProduto(produtoService.buscarPorId(produtoId));
        t.setUnidadeMedida(unidadeService.buscarPorId(unidadeId));
        t.setTabValorEnergetico(tabValorEnergetico != null ? tabValorEnergetico : 0.0);
        t.setTabValorEnergeticoPorcao(tabValorEnergeticoPorcao != null ? tabValorEnergeticoPorcao : 0.0);
        t.setTabPorcao(tabPorcao != null ? tabPorcao : 0.0);
        t.setTabTotalPorcao(tabTotalPorcao != null ? tabTotalPorcao : 0.0);
        t.setTabTotalColheres(tabTotalColheres != null ? tabTotalColheres : 0.0);
        t.setTabVD(tabVD != null ? tabVD : 0.0);

        if (isInsert) {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("uid") instanceof String uid) {
                t.setUsuario(usuarioRepository.findByFirebaseUid(uid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
            }
        }

        TabelaNutricional salva = tabelaService.salvar(t);

        if (isInsert) {
            // Redireciona para inserção de nutrientes, orientando o usuário
            return "redirect:/nutrientes/valores/inserir/" + salva.getTabCodigo();
        }
        return "redirect:/tabela";
    }

    // =========================
    // ALTERAR
    // =========================

    @GetMapping("/alterar/{id}")
    public String formAlterar(@PathVariable Long id, HttpServletRequest request, Model model) {
        TabelaNutricional tabela = tabelaService.buscarPorId(id);
        rotuloAuthorizationService.exigirPermissaoDeEdicao(tabela, request);
        model.addAttribute("tabela", tabela);
        model.addAttribute("produtos", produtoService.listarTodos());
        model.addAttribute("unidades", unidadeService.listarTodos());
        model.addAttribute("elementos", elementoService.listarTodos());
        return "alterarTabela";
    }

    // =========================
    // REMOVER
    // =========================

    @GetMapping("/remover/{id}")
    public String formRemover(@PathVariable Long id, HttpServletRequest request, Model model) {
        TabelaNutricional tabela = tabelaService.buscarPorId(id);
        rotuloAuthorizationService.exigirPermissaoDeEdicao(tabela, request);
        model.addAttribute("tabela", tabela);
        return "removerTabela";
    }

    @PostMapping("/remover/{id}")
    public String remover(@PathVariable Long id, HttpServletRequest request) {
        rotuloAuthorizationService.exigirPermissaoDeEdicao(tabelaService.buscarPorId(id), request);
        tabelaService.deletar(id);
        return "redirect:/tabela";
    }

    // =========================
    // VISUALIZAR RÓTULO
    // =========================

    @GetMapping("/visualizar/{id}")
    public String visualizarRotulo(@PathVariable Long id, Model model) {
        TabelaNutricional tabela = tabelaService.buscarPorId(id);
        model.addAttribute("tabela", tabela);
        model.addAttribute("elementos", tabela.getTneElementos());
        return "visualizarTabela";
    }

    // =========================
    // IMPRIMIR
    // =========================

    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable Long id, Model model) {
        TabelaNutricional tabela = tabelaService.buscarPorId(id);
        model.addAttribute("tabela", tabela);
        model.addAttribute("elementos", tabela.getTneElementos());
        model.addAttribute("unidade", tabela.getUnidadeMedida());
        return "imprimirTabela";
    }

    // =========================
    // PDF
    // =========================

    @GetMapping("/gerar-pdf/{id}")
    public ResponseEntity<byte[]> gerarPdf(@PathVariable Long id) {
        try {
            byte[] pdf = tabelaService.gerarPdf(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=tabela_" + id + ".pdf")
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}