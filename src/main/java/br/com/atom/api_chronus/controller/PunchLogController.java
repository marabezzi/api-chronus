package br.com.atom.api_chronus.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.api_chronus.dto.PunchLogDTO;
import br.com.atom.api_chronus.dto.PunchLogResponseDTO;
import br.com.atom.api_chronus.service.PunchLogService;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para batidas de ponto do iDClass.
 *
 * Endpoints:
 *   GET /api/ponto                      → primeira página (NSR 1)
 *   GET /api/ponto?startNsr=51          → página a partir do NSR 51
 *   GET /api/ponto/todas                → todas as batidas (até 25k registros)
 *   GET /api/ponto/funcionario/{pis}    → batidas de um funcionário pelo PIS/CPF
 */
@RestController
@RequestMapping("/api/ponto")
@RequiredArgsConstructor
public class PunchLogController {

    private final PunchLogService punchLogService;

    /**
     * GET /api/ponto
     * GET /api/ponto?startNsr=51
     *
     * Retorna uma página de batidas a partir do NSR informado.
     * Use "next_nsr" da resposta como startNsr na próxima chamada.
     *
     * 200: { "punch_logs": [...], "total": 150, "next_nsr": 51 }
     * 503: { "erro": "..." }
     */
    @GetMapping
    public ResponseEntity<?> buscarPagina(
            @RequestParam(required = false) Long startNsr) {

        PunchLogResponseDTO resultado = punchLogService.buscarPagina(startNsr);

        if (resultado != null) {
            return ResponseEntity.ok(resultado);
        }

        return ResponseEntity
                .status(503)
                .body(Map.of("erro", "Não foi possível buscar as batidas de ponto"));
    }

    /**
     * GET /api/ponto/todas
     *
     * Retorna todas as batidas percorrendo todas as páginas automaticamente.
     * Use com critério — pode retornar até 25.000 registros.
     *
     * 200: [ {...batida1}, {...batida2}, ... ]
     * 204: sem registros
     */
    @GetMapping("/todas")
    public ResponseEntity<List<PunchLogDTO>> buscarTodas() {

        List<PunchLogDTO> batidas = punchLogService.buscarTodas();

        if (batidas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(batidas);
    }

    /**
     * GET /api/ponto/funcionario/{pis}
     *
     * Retorna as batidas de um funcionário pelo PIS ou CPF.
     * Compatível com firmware pré-671 (PIS) e 671 (CPF).
     *
     * 200: [ {...batida1}, {...batida2} ]
     * 204: nenhuma batida para este funcionário
     */
    @GetMapping("/funcionario/{pis}")
    public ResponseEntity<List<PunchLogDTO>> buscarPorPis(
            @PathVariable String pis) {

        List<PunchLogDTO> batidas = punchLogService.buscarPorPis(pis);

        if (batidas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(batidas);
    }
}
