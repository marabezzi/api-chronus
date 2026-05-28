package br.com.atom.api_chronus.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.api_chronus.dto.AfdLineDTO;
import br.com.atom.api_chronus.dto.AfdResponseDTO;
import br.com.atom.api_chronus.service.PunchLogService;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para registros de ponto do iDClass.
 *
 * Endpoints:
 *   GET /api/ponto                    → todas as batidas (AFD completo)
 *   GET /api/ponto?initialNsr=100     → batidas a partir do NSR 100
 *   GET /api/ponto/funcionario/{pis}  → batidas de um funcionário
 */
@RestController
@RequestMapping("/api/ponto")
@RequiredArgsConstructor
public class PunchLogController {

    private final PunchLogService punchLogService;

    /**
     * GET /api/ponto
     * GET /api/ponto?initialNsr=100
     *
     * Retorna todas as batidas parseadas do AFD.
     *
     * 200: { "totalLinhas": 500, "totalBatidas": 498, "batidas": [...] }
     * 503: { "erro": "..." }
     */
    @GetMapping
    public ResponseEntity<?> buscarBatidas(
            @RequestParam(required = false) Long initialNsr) {

        AfdResponseDTO resultado = punchLogService.buscarBatidas(initialNsr);

        if (resultado != null) {
            return ResponseEntity.ok(resultado);
        }

        return ResponseEntity
                .status(503)
                .body(Map.of("erro", "Não foi possível buscar os registros de ponto"));
    }

    /**
     * GET /api/ponto/funcionario/{pis}
     *
     * Retorna batidas de um funcionário pelo PIS ou CPF.
     * Aceita com ou sem formatação: "12345678900" ou "123.456.789-00"
     *
     * 200: [ { "nsr": 1853, "dateTime": "2026-05-30T18:02", ... } ]
     * 204: nenhuma batida encontrada
     */
    @GetMapping("/funcionario/{pis}")
    public ResponseEntity<List<AfdLineDTO>> buscarPorPis(
            @PathVariable String pis) {

        List<AfdLineDTO> batidas = punchLogService.buscarPorPis(pis);

        if (batidas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(batidas);
    }
}