package br.com.atom.api_chronus.controller;

import br.com.atom.api_chronus.entity.ConfiguracaoSistema;
import br.com.atom.api_chronus.service.ConfiguracaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller REST para configurações do sistema.
 *
 * Endpoints:
 *   GET  /api/config               → todas as configs agrupadas
 *   GET  /api/config/{categoria}   → configs de uma categoria
 *   PUT  /api/config/{chave}       → atualiza uma config
 *   PUT  /api/config/lote          → atualiza várias de uma vez
 *
 * Categorias disponíveis: SYNC, EMPRESA, EMAIL, GERAL
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfiguracaoController {

    private final ConfiguracaoService service;

    /**
     * GET /api/config
     * Lista todas as configurações agrupadas por categoria.
     * Valores sensíveis (senhas) são mascarados com "***".
     */
    @GetMapping
    public ResponseEntity<Map<String, List<ConfiguracaoSistema>>>
            listarTodas() {
        Map<String, List<ConfiguracaoSistema>> configs =
                service.listarTodas();
        mascararSensiveis(configs);
        return ResponseEntity.ok(configs);
    }

    /**
     * GET /api/config/{categoria}
     * Lista configurações de uma categoria.
     * Categorias: SYNC, EMPRESA, EMAIL, GERAL
     */
    @GetMapping("/{categoria}")
    public ResponseEntity<?> listarPorCategoria(
            @PathVariable String categoria) {
        List<ConfiguracaoSistema> lista =
                service.listarPorCategoria(categoria);
        if (lista.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("erro",
                            "Categoria nao encontrada: " + categoria));
        }
        lista.forEach(c -> {
            if (Boolean.TRUE.equals(c.getSensivel())) {
                c.setValor("***");
            }
        });
        return ResponseEntity.ok(lista);
    }

    /**
     * PUT /api/config/{chave}
     * Atualiza o valor de uma configuração.
     *
     * Body: { "valor": "novo_valor" }
     *
     * Exemplos:
     *   PUT /api/config/sync.intervalo.minutos  → { "valor": "10" }
     *   PUT /api/config/email.habilitado        → { "valor": "true" }
     *   PUT /api/config/empresa.cnpj            → { "valor": "74433293000137" }
     */
    @PutMapping("/{chave}")
    public ResponseEntity<?> atualizar(
            @PathVariable String chave,
            @RequestBody Map<String, String> body) {

        String novoValor = body.get("valor");
        if (novoValor == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Campo obrigatorio: valor"));
        }

        try {
            ConfiguracaoSistema config =
                    service.atualizar(chave, novoValor);
            if (Boolean.TRUE.equals(config.getSensivel())) {
                config.setValor("***");
            }
            return ResponseEntity.ok(config);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * PUT /api/config/lote
     * Atualiza múltiplas configurações de uma vez.
     *
     * Body:
     * {
     *   "empresa.razao.social": "MINHA EMPRESA LTDA",
     *   "empresa.cnpj":         "12345678000199",
     *   "email.habilitado":     "true",
     *   "sync.intervalo.minutos": "10"
     * }
     */
    @PutMapping("/lote")
    public ResponseEntity<?> atualizarLote(
            @RequestBody Map<String, String> valores) {

        if (valores == null || valores.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Nenhuma configuracao informada"));
        }

        service.atualizarLote(valores);
        return ResponseEntity.ok(
                Map.of("mensagem",
                        valores.size() + " configuracoes atualizadas."));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private void mascararSensiveis(
            Map<String, List<ConfiguracaoSistema>> configs) {
        configs.values().forEach(lista ->
                lista.forEach(c -> {
                    if (Boolean.TRUE.equals(c.getSensivel())) {
                        c.setValor("***");
                    }
                })
        );
    }
}