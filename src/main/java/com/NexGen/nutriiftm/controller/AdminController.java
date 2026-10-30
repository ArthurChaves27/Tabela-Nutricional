package com.NexGen.nutriiftm.controller;

import com.NexGen.nutriiftm.service.AdminDashboardService;
import com.NexGen.nutriiftm.service.TabelaNutricionalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Painel administrativo: visão consolidada de todos os rótulos gerados
 * no sistema, com estatísticas e gráficos de barra (Chart.js).
 *
 * Protegido pelo AuthInterceptor: exige sessão válida e papel administrativo.
 */
@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final TabelaNutricionalService tabelaService;
    private final ObjectMapper objectMapper;

    @GetMapping("/dashboard")
    public String dashboard(Model model) throws Exception {
        var stats = dashboardService.montarEstatisticas();

        model.addAttribute("stats", stats);
        model.addAttribute("tabelas", tabelaService.listarTodos());

        // Serializado como JSON para os gráficos (Chart.js) no template.
        model.addAttribute("produtosPorFabricanteJson", objectMapper.writeValueAsString(stats.getProdutosPorFabricante()));
        model.addAttribute("produtosPorCooperativaJson", objectMapper.writeValueAsString(stats.getProdutosPorCooperativa()));
        model.addAttribute("caloriasMediaPorFabricanteJson", objectMapper.writeValueAsString(stats.getCaloriasMediaPorFabricante()));
        model.addAttribute("distribuicaoCaloricaJson", objectMapper.writeValueAsString(stats.getDistribuicaoCalorica()));
        model.addAttribute("rotulosPorUnidadeMedidaJson", objectMapper.writeValueAsString(stats.getRotulosPorUnidadeMedida()));

        return "adminDashboard";
    }
}