package com.NexGen.nutriiftm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;

import com.NexGen.nutriiftm.model.TabNutElemento;
import com.NexGen.nutriiftm.model.TabelaNutricional;
import com.NexGen.nutriiftm.security.RotuloAuthorizationService;
import com.NexGen.nutriiftm.service.TabNutElementoService;
import com.NexGen.nutriiftm.service.TabelaNutricionalService;
import com.NexGen.nutriiftm.service.ElementoService;

@Controller
@RequestMapping("/nutrientes/valores")
@RequiredArgsConstructor
public class TabNutElementoController {

    private final TabNutElementoService tabNutService;
    private final TabelaNutricionalService tabelaService;
    private final ElementoService elementoService;
    private final RotuloAuthorizationService rotuloAuthorizationService;

    @GetMapping("/inserir/{tabId}")
    public String formInserir(@PathVariable Long tabId, HttpServletRequest request, Model model) {
        TabelaNutricional tabela = tabelaService.buscarPorId(tabId);
        rotuloAuthorizationService.exigirPermissaoDeEdicao(tabela, request);
        model.addAttribute("tne", new TabNutElemento());
        model.addAttribute("tabela", tabela);
        model.addAttribute("elementos", elementoService.listarTodos());
        return "inserirTabela";
    }

    @PostMapping("/salvar/{tabId}")
    public String salvar(@PathVariable Long tabId, TabNutElemento tne, HttpServletRequest request) {
        TabelaNutricional tabela = tabelaService.buscarPorId(tabId);
        rotuloAuthorizationService.exigirPermissaoDeEdicao(tabela, request);
        tne.setTabelaNutricional(tabela);
        double porcao = tabela.getTabPorcao();
        tabNutService.salvarComCalculo(tne, porcao);
        return "redirect:/tabela/imprimir/" + tabId;
    }

    @GetMapping("/alterar/{id}")
    public String formAlterar(@PathVariable Long id, HttpServletRequest request, Model model) {
        TabNutElemento tne = tabNutService.buscarPorId(id);
        Long tabId = tne.getTabelaNutricional().getTabCodigo();
        rotuloAuthorizationService.exigirPermissaoDeEdicao(tabelaService.buscarPorId(tabId), request);
        model.addAttribute("tne", tne);
        model.addAttribute("elementos", elementoService.listarTodos());
        return "alterarNutriente";
    }

    @PostMapping("/remover/{id}")
    public String remover(@PathVariable Long id, HttpServletRequest request) {
        TabNutElemento tne = tabNutService.buscarPorId(id);
        Long tabId = tne.getTabelaNutricional().getTabCodigo();
        rotuloAuthorizationService.exigirPermissaoDeEdicao(tabelaService.buscarPorId(tabId), request);
        tabNutService.deletar(id);
        return "redirect:/tabela/imprimir/" + tabId;
    }
}