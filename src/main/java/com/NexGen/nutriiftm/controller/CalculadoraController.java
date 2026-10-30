package com.NexGen.nutriiftm.controller;

import com.NexGen.nutriiftm.domain.nutricional.ArredondamentoAnvisa;
import com.NexGen.nutriiftm.model.*;
import com.NexGen.nutriiftm.service.*;
import com.NexGen.nutriiftm.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

/**
 * Correções aplicadas:
 *  #2  — tneValorPadrao calculado via ArredondamentoAnvisa (único ponto, fallback correto).
 *  #3  — tneVD calculado via ArredondamentoAnvisa.percentualVD (sem -0.0 nem NaN).
 *  #7  — salvar() ignora campos editáveis do rótulo; usa valores calculados pelo backend.
 *  #10 — JSON parseado com Jackson em vez de regex caseiro.
 *  #13 — totalPorcoes limitado a MAX_PORCOES_EMBALAGEM.
 */
@Slf4j
@Controller
@RequestMapping("/calculadora")
@RequiredArgsConstructor
public class CalculadoraController {

    private final TACOService              tacoService;
    private final MacronutrientesService   macroService;
    private final TabelaNutricionalService tabelaService;
    private final ProdutoService           produtoService;
    private final FabricanteService        fabricanteService;
    private final UnidadeMedidaService     unidadeService;
    private final ElementoRepository       elementoRepo;
    private final TabNutElementoRepository tneRepo;
    private final UsuarioRepository       usuarioRepo;
    private final ObjectMapper             objectMapper;  // injetado pelo Spring Boot

    private static final int MAX_PORCOES = 9999;
    private static final double KCAL_TO_KJ = 4.184;

    // ── GET /calculadora ──────────────────────────────────────────────────────
    @GetMapping
    public String form(Model model) {
        model.addAttribute("produtos",    produtoService.listarTodos());
        model.addAttribute("fabricantes", fabricanteService.listarTodos());
        model.addAttribute("unidades",    unidadeService.listarTodos());
        return "calculadora";
    }

    // ── GET /calculadora/produto/{id}/dados ────────────────────────────────────
    /**
     * Auto-preenchimento ao selecionar um produto existente na calculadora.
     * Retorna a última tabela nutricional salva desse produto (se houver),
     * já no mesmo formato usado por preencherRotulo() no front-end, para que
     * o rótulo apareça pronto e editável sem precisar recalcular pelos
     * ingredientes.
     */
    @GetMapping("/produto/{id}/dados")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> dadosProduto(@PathVariable Long id) {
        Produto produto;
        try {
            produto = produtoService.buscarPorId(id);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ingredientesTexto", produto.getProIngredientes());
        Optional<TabelaNutricional> tabelaOpt = tabelaService.buscarUltimaPorProduto(id);

        if (tabelaOpt.isEmpty()) {
            resp.put("temTabela", false);
            return ResponseEntity.ok(resp);
        }

        TabelaNutricional t = tabelaOpt.get();
        resp.put("temTabela", true);
        resp.put("porcao", t.getTabPorcao());
        resp.put("unidadeId", t.getUnidadeMedida() != null ? t.getUnidadeMedida().getUndCodigo() : null);
        resp.put("totalPorcoes", t.getTabTotalPorcao());
        resp.put("medidaCaseira", t.getTabMedidaCaseira());

        Map<String, Object> nut = new LinkedHashMap<>();
        double energia100g = nz(t.getTabValorEnergetico());
        double energiaPorcao = nz(t.getTabValorEnergeticoPorcao());
        nut.put("energia100g",     energia100g);
        nut.put("energiaKj100g",   energia100g * KCAL_TO_KJ);
        nut.put("energiaPorcao",   energiaPorcao);
        nut.put("energiaKjPorcao", energiaPorcao * KCAL_TO_KJ);
        nut.put("vdEnergia",       t.getTabVD());

        for (TabNutElemento tne : t.getTneElementos()) {
            if (tne.getElemento() == null) continue;
            long elId = tne.getElemento().getEleCodigo();
            switch ((int) elId) {
                case 1 -> {
                    nut.put("carboidrato100g",   tne.getTneValorPadrao());
                    nut.put("carboidratoPorcao", tne.getTneValor());
                    nut.put("vdCarb",            tne.getTneVD());
                }
                case 2 -> {
                    nut.put("acucaresTotal100g",   tne.getTneValorPadrao());
                    nut.put("acucaresTotalPorcao", tne.getTneValor());
                }
                case 3 -> nut.put("acucaresAdicionadosPorcao", tne.getTneValor());
                case 4 -> {
                    nut.put("proteina100g",   tne.getTneValorPadrao());
                    nut.put("proteinaPorcao", tne.getTneValor());
                    nut.put("vdProt",         tne.getTneVD());
                }
                case 5 -> {
                    nut.put("lipideos100g",   tne.getTneValorPadrao());
                    nut.put("lipideosPorcao", tne.getTneValor());
                    nut.put("vdLip",          tne.getTneVD());
                }
                case 6 -> {
                    nut.put("saturado100g",   tne.getTneValorPadrao());
                    nut.put("saturadoPorcao", tne.getTneValor());
                    nut.put("vdSat",          tne.getTneVD());
                }
                case 7 -> {
                    nut.put("trans100g",   tne.getTneValorPadrao());
                    nut.put("transPorcao", tne.getTneValor());
                }
                case 15 -> {
                    nut.put("fibra100g",   tne.getTneValorPadrao());
                    nut.put("fibraPorcao", tne.getTneValor());
                    nut.put("vdFibra",     tne.getTneVD());
                }
                case 16 -> {
                    nut.put("sodio100g",   tne.getTneValorPadrao());
                    nut.put("sodioPorcao", tne.getTneValor());
                    nut.put("vdSodio",     tne.getTneVD());
                }
                default -> { /* nutriente sem campo correspondente no rótulo simplificado */ }
            }
        }
        resp.put("nutrientes", nut);
        return ResponseEntity.ok(resp);
    }

    // ── GET /calculadora/taco-dados ───────────────────────────────────────────
    @GetMapping("/taco-dados")
    @ResponseBody
    public List<Map<String, Object>> tacoDados() {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (ItemTACO item : tacoService.buscarTodos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("codigo",    item.getCodigo());
            m.put("descricao", item.getDescricao());
            m.put("categoria", item.getCategoria());
            m.put("energia",   item.getEnergia());
            lista.add(m);
        }
        return lista;
    }

    // ── POST /calculadora/calcular ────────────────────────────────────────────
    @PostMapping("/calcular")
    public ResponseEntity<Map<String, Object>> calcular(
            @RequestBody List<Map<String, Object>> ingredientes,
            @RequestParam(defaultValue = "100") double porcao
    ) {
        if (ingredientes == null || ingredientes.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Nenhum ingrediente informado."));
        }
        try {
            Receita receita = montarReceita(ingredientes, porcao);
            ValoresNutricionais v = macroService.calcular(receita);
            return ResponseEntity.ok(nutrientesParaMap(v, receita.getPesoTotalG()));
        } catch (IllegalArgumentException e) {
            log.warn("Cálculo rejeitado: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    // ── POST /calculadora/salvar ──────────────────────────────────────────────
    @PostMapping("/salvar")
    public String salvar(
            @RequestParam(required = false) Long    produtoId,
            @RequestParam(required = false) String  novoNome,
            @RequestParam(required = false) String  novoNomeFantasia,
            @RequestParam(required = false) Long    novoFabricanteId,
            @RequestParam(required = false) String  novoDataFabricacao,
            @RequestParam(required = false) String  novoDataVencimento,
            @RequestParam(required = false, defaultValue = "0") double novoPeso,
            @RequestParam(required = false) String  novoRecomendacoes,
            @RequestParam                   Long    unidadeId,
            @RequestParam                   double  porcao,
            @RequestParam                   double  totalPorcoes,
            @RequestParam(required = false, defaultValue = "0") double totalColheres,
            @RequestParam(required = false) String  ingredientesJson,
            @RequestParam(required = false) String  medidaCaseira,
            @RequestParam(required = false) String  rotuloJson,
            HttpServletRequest request,
            Model model
    ) {
        // CORREÇÃO #10: Jackson em vez de regex caseiro
        List<Map<String, Object>> ingredientesLista = parsearIngredientesJson(ingredientesJson);

        // O rótulo editável é a fonte da verdade quando presente (decisão do usuário:
        // o que foi digitado/corrigido no rótulo é o que deve ser salvo). Sem ele,
        // cai no comportamento legado: recalcula tudo a partir dos ingredientes.
        RotuloEditado editado = parsearRotuloJson(rotuloJson);

        if (editado == null && ingredientesLista.isEmpty()) {
            model.addAttribute("erro", "Adicione ingredientes ou calcule o rótulo antes de salvar.");
            return form(model);
        }

        // CORREÇÃO #13: limitar totalPorcoes
        int porcoesSanitizado = Math.min(MAX_PORCOES,
                Math.max(1, (int) Math.floor(totalPorcoes)));

        String listaIngredientes = ingredientesLista.isEmpty() ? null : montarStringIngredientes(ingredientesLista);
        Produto produto = resolverProduto(produtoId, novoNome, novoNomeFantasia,
                novoFabricanteId, novoDataFabricacao, novoDataVencimento,
                novoPeso, novoRecomendacoes, listaIngredientes);

        ValoresNutricionais v = null;
        if (editado == null) {
            Receita receita = montarReceita(ingredientesLista, porcao);
            try {
                v = macroService.calcular(receita);
            } catch (IllegalArgumentException e) {
                log.warn("Nenhum ingrediente TBCA encontrado ao salvar: {}", e.getMessage());
                model.addAttribute("erro", e.getMessage());
                return form(model);
            }
        }

        TabelaNutricional tabela = new TabelaNutricional();
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("uid") instanceof String uid) {
            tabela.setUsuario(usuarioRepo.findByFirebaseUid(uid)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)));
        }
        tabela.setProduto(produto);
        tabela.setUnidadeMedida(unidadeService.buscarPorId(unidadeId));
        tabela.setTabPorcao(porcao);
        tabela.setTabTotalPorcao((double) porcoesSanitizado);
        tabela.setTabTotalColheres(totalColheres);
        if (medidaCaseira != null && !medidaCaseira.isBlank()) {
            tabela.setTabMedidaCaseira(medidaCaseira.trim());
        }
        tabela.setTabPorcaoPadrao(porcao);

        if (editado != null) {
            // Valores exatamente como aprovados/editados pelo usuário no rótulo.
            tabela.setTabValorEnergeticoPorcao(nz(editado.energiaPorcao));
            tabela.setTabValorEnergetico(nz(editado.energia100g));
            tabela.setTabVD(nz(editado.vdEnergia));
        } else {
            tabela.setTabValorEnergeticoPorcao((double) v.getEnergiaPorcaoKcal());
            tabela.setTabValorEnergetico((double) v.getEnergia100kcal());
            tabela.setTabVD((double) v.getVDEnergia());
        }

        TabelaNutricional salva = tabelaService.salvar(tabela);

        if (editado != null) {
            salvarNutrientesEditados(salva, editado);
        } else {
            salvarNutrientes(salva, v, porcao);
        }

        return "redirect:/tabela";
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private Produto resolverProduto(Long produtoId, String nome, String nomeFantasia,
            Long fabId, String dataFab, String dataVenc,
            double peso, String recomendacoes, String ingredientes) {
        if (produtoId != null && produtoId > 0) {
            return produtoService.buscarPorId(produtoId);
        }
        if (fabId == null || fabId <= 0) {
            throw new IllegalArgumentException("Fabricante obrigatório para novo produto.");
        }
        Produto p = new Produto();
        p.setProNome(nome);
        p.setProNomeFantasia(nomeFantasia);
        p.setFabricante(fabricanteService.buscarPorId(fabId));
        if (dataFab  != null && !dataFab.isBlank())  p.setProDataFabricacao(LocalDate.parse(dataFab));
        if (dataVenc != null && !dataVenc.isBlank())  p.setProDataVencimento(LocalDate.parse(dataVenc));
        p.setProPeso(peso);
        p.setProRecomendacoes(recomendacoes);
        p.setProIngredientes(ingredientes != null ? ingredientes : "");
        return produtoService.salvar(p);
    }

    private void salvarNutrientes(TabelaNutricional tabela, ValoresNutricionais v, double porcao) {
        // IDs conforme nutricional.sql — nunca usar nomes textuais
        Map<Long, Double> nutrientesPorId = new LinkedHashMap<>();
        nutrientesPorId.put(1L,  v.getCarboidratoPorcao());
        nutrientesPorId.put(4L,  v.getProteinaPorcao());
        nutrientesPorId.put(5L,  v.getLipideosPorcao());
        nutrientesPorId.put(6L,  v.getSaturadoPorcao());
        nutrientesPorId.put(7L,  0.0);  // Gorduras Trans — sem dado TBCA
        nutrientesPorId.put(15L, v.getFibraPorcao());
        nutrientesPorId.put(16L, (double) v.getSodioPorcaoMg());

        Map<Long, Elemento> elementoPorId = new HashMap<>();
        elementoRepo.findAll().forEach(el -> elementoPorId.put(el.getEleCodigo(), el));

        List<TabNutElemento> tneList = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : nutrientesPorId.entrySet()) {
            Elemento elemento = elementoPorId.get(entry.getKey());
            if (elemento == null) continue;

            double valorPorcao = entry.getValue();

            // CORREÇÃO #2/#3: único lugar da verdade — sem duplicação de lógica
            TabNutElemento tne = new TabNutElemento();
            tne.setTabelaNutricional(tabela);
            tne.setElemento(elemento);
            tne.setTneValor(valorPorcao);
            tne.setTneValorPadrao(ArredondamentoAnvisa.valorPor100g(valorPorcao, porcao));
            tne.setTneVD(ArredondamentoAnvisa.percentualVD(valorPorcao, elemento.getEleValorRecomendado()));
            tneList.add(tne);
        }
        tneRepo.saveAll(tneList);
    }

    /**
     * Salva os nutrientes exatamente como aprovados no rótulo editável.
     * CORREÇÃO: inclui Açúcares Totais (2) e Açúcares Adicionados (3), que
     * antes nunca eram persistidos mesmo aparecendo calculados na tela.
     */
    private void salvarNutrientesEditados(TabelaNutricional tabela, RotuloEditado e) {
        Map<Long, Elemento> elementoPorId = new HashMap<>();
        elementoRepo.findAll().forEach(el -> elementoPorId.put(el.getEleCodigo(), el));

        record Linha(long elementoId, Double porcao, Double cem, Double vd) {}
        List<Linha> linhas = List.of(
                new Linha(1L,  e.carboidratoPorcao,          e.carboidrato100g,          e.vdCarb),
                new Linha(2L,  e.acucaresTotalPorcao,         e.acucaresTotal100g,        null),
                // Açúcares adicionados: a base TBCA não declara esse valor (N.D. por definição —
                // é uma decisão de formulação do produtor), por isso o rótulo só expõe o campo
                // "por porção" para edição manual; o valor por 100g fica 0 (não exibido na tela).
                new Linha(3L,  e.acucaresAdicionadosPorcao,   0.0,                        null),
                new Linha(4L,  e.proteinaPorcao,              e.proteina100g,             e.vdProt),
                new Linha(5L,  e.lipideosPorcao,              e.lipideos100g,             e.vdLip),
                new Linha(6L,  e.saturadoPorcao,              e.saturado100g,             e.vdSat),
                new Linha(7L,  e.transPorcao,                 e.trans100g,                null),
                new Linha(15L, e.fibraPorcao,                 e.fibra100g,                e.vdFibra),
                new Linha(16L, e.sodioPorcao,                 e.sodio100g,                e.vdSodio)
        );

        List<TabNutElemento> tneList = new ArrayList<>();
        for (Linha l : linhas) {
            Elemento elemento = elementoPorId.get(l.elementoId());
            if (elemento == null) continue;
            TabNutElemento tne = new TabNutElemento();
            tne.setTabelaNutricional(tabela);
            tne.setElemento(elemento);
            tne.setTneValor(nz(l.porcao()));
            tne.setTneValorPadrao(nz(l.cem()));
            tne.setTneVD(l.vd() != null ? l.vd() : 0.0);
            tneList.add(tne);
        }
        tneRepo.saveAll(tneList);
    }

    private static double nz(Double d) { return d != null ? d : 0.0; }

    /** Parseia o JSON com os valores editados no rótulo. Retorna null se ausente/inválido. */
    private RotuloEditado parsearRotuloJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, RotuloEditado.class);
        } catch (Exception e) {
            log.warn("rotuloJson inválido, caindo para recálculo pelos ingredientes: {}", json, e);
            return null;
        }
    }

    /** Espelha os campos enviados pelo JS a partir dos inputs rv-* do rótulo. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class RotuloEditado {
        public Double energia100g, energiaPorcao, vdEnergia;
        public Double carboidrato100g, carboidratoPorcao, vdCarb;
        public Double acucaresTotal100g, acucaresTotalPorcao;
        public Double acucaresAdicionadosPorcao;
        public Double proteina100g, proteinaPorcao, vdProt;
        public Double lipideos100g, lipideosPorcao, vdLip;
        public Double saturado100g, saturadoPorcao, vdSat;
        public Double trans100g, transPorcao;
        public Double fibra100g, fibraPorcao, vdFibra;
        public Double sodio100g, sodioPorcao, vdSodio;
    }

    private Receita montarReceita(List<Map<String, Object>> ingredientes, double porcao) {
        Receita receita = new Receita();
        receita.setPorcaoG(porcao);
        List<IngredienteReceita> lista = new ArrayList<>();
        for (Map<String, Object> ing : ingredientes) {
            String nome = (String) ing.get("nome");
            double qtd  = ((Number) ing.get("quantidade")).doubleValue();
            // CORREÇÃO #6: busca por nome feita uma única vez aqui
            ItemTACO item = tacoService.buscarPorNome(nome);
            IngredienteReceita ir = new IngredienteReceita();
            ir.setNome(nome);
            ir.setQuantidadeG(qtd);
            ir.setItemTACO(item);
            lista.add(ir);
        }
        receita.setIngredientes(lista);
        return receita;
    }

    /** CORREÇÃO #10: Jackson em vez de regex */
    private List<Map<String, Object>> parsearIngredientesJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("JSON de ingredientes inválido: {}", json, e);
            return Collections.emptyList();
        }
    }

    private String montarStringIngredientes(List<Map<String, Object>> lista) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> ing : lista) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(ing.get("nome"))
              .append(" (").append(ing.get("quantidade")).append("g)");
        }
        return sb.toString();
    }

    private Map<String, Object> nutrientesParaMap(ValoresNutricionais v, double pesoTotal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pesoTotal",                 pesoTotal);
        // 100g
        m.put("energia100g",               v.getEnergia100kcal());
        m.put("energiaKj100g",             v.getEnergia100kj());
        m.put("carboidrato100g",           v.getCarboidrato100g());
        m.put("acucaresTotal100g",         v.getAcucaresTotal100g());
        m.put("acucaresAdicionados100g",   0.0);  // N.D.
        m.put("proteina100g",              v.getProteina100g());
        m.put("lipideos100g",              v.getLipideos100g());
        m.put("saturado100g",              v.getSaturado100g());
        m.put("fibra100g",                 v.getFibra100g());
        m.put("sodio100g",                 v.getSodio100mg());
        // Porção
        m.put("energiaPorcao",             v.getEnergiaPorcaoKcal());
        m.put("energiaKjPorcao",           v.getEnergiaPorcaoKj());
        m.put("carboidratoPorcao",         v.getCarboidratoPorcao());
        m.put("acucaresTotalPorcao",       v.getAcucaresTotalPorcao());
        m.put("acucaresAdicionadosPorcao", 0.0);  // N.D.
        m.put("proteinaPorcao",            v.getProteinaPorcao());
        m.put("lipideosPorcao",            v.getLipideosPorcao());
        m.put("saturadoPorcao",            v.getSaturadoPorcao());
        m.put("fibraPorcao",               v.getFibraPorcao());
        m.put("sodioPorcao",               v.getSodioPorcaoMg());
        // %VD
        m.put("vdEnergia",                 v.getVDEnergia());
        m.put("vdCarb",                    v.getVDCarboidrato());
        m.put("vdProt",                    v.getVDProteina());
        m.put("vdLip",                     v.getVDLipideos());
        m.put("vdSat",                     v.getVDSaturado());
        m.put("vdFibra",                   v.getVDFibra());
        m.put("vdSodio",                   v.getVDSodio());
        return m;
    }
}