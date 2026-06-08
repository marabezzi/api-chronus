package br.com.atom.api_chronus.controller;

import br.com.atom.api_chronus.dto.RelatorioHorasFuncionarioDTO;
import br.com.atom.api_chronus.dto.RelatorioHorasResponseDTO;
import br.com.atom.api_chronus.service.RelatorioHorasService;
import br.com.atom.api_chronus.service.RelatorioHorasPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller REST para Relatório de Horas.
 *
 * Endpoints:
 *   POST /api/relatorio/horas/todos          → todos os funcionários (JSON)
 *   POST /api/relatorio/horas/todos/pdf      → todos os funcionários (PDF)
 *   POST /api/relatorio/horas/funcionario    → um funcionário (JSON)
 *   POST /api/relatorio/horas/funcionario/pdf → um funcionário (PDF)
 */
@RestController
@RequestMapping("/api/relatorio/horas")
@RequiredArgsConstructor
public class RelatorioHorasController {

    private final RelatorioHorasService    relatorioService;
    private final RelatorioHorasPdfService pdfService;

    /**
     * POST /api/relatorio/horas/todos
     *
     * Relatório JSON de todos os funcionários no período.
     *
     * Body: { "dataInicial": "01052026", "dataFinal": "31052026" }
     */
    @PostMapping("/todos")
    public ResponseEntity<?> todos(@RequestBody Map<String, String> body) {
        String di = body.get("dataInicial");
        String df = body.get("dataFinal");

        if (di == null || df == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro",
                            "Campos obrigatórios: dataInicial, dataFinal"));
        }

        try {
            RelatorioHorasResponseDTO rel =
                    relatorioService.gerarTodos(di, df);
            return ResponseEntity.ok(rel);
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * POST /api/relatorio/horas/todos/pdf
     *
     * Relatório PDF de todos os funcionários no período.
     */
    @PostMapping(value = "/todos/pdf", produces = "application/pdf")
    public ResponseEntity<?> todosPdf(@RequestBody Map<String, String> body) {
        String di = body.get("dataInicial");
        String df = body.get("dataFinal");

        if (di == null || df == null) {
            return ResponseEntity.badRequest()
                    .body("Campos obrigatórios: dataInicial, dataFinal");
        }

        try {
            RelatorioHorasResponseDTO rel =
                    relatorioService.gerarTodos(di, df);
            byte[] pdf = pdfService.gerarTodos(rel);

            if (pdf == null) {
                return ResponseEntity.status(503)
                        .body("Erro ao gerar PDF");
            }

            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=\"Relatorio_Horas_"
                            + di + "_" + df + ".pdf\"")
                    .header("Content-Type", "application/pdf")
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * POST /api/relatorio/horas/funcionario
     *
     * Relatório JSON de um único funcionário.
     *
     * Body: { "pis": "012952592162",
     *         "dataInicial": "01052026", "dataFinal": "31052026" }
     */
    @PostMapping("/funcionario")
    public ResponseEntity<?> funcionario(
            @RequestBody Map<String, String> body) {

        String pis = body.get("pis");
        String di  = body.get("dataInicial");
        String df  = body.get("dataFinal");

        if (pis == null || di == null || df == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro",
                            "Campos obrigatórios: pis, dataInicial, dataFinal"));
        }

        try {
            RelatorioHorasFuncionarioDTO rel =
                    relatorioService.gerarPorPis(pis, di, df);
            return ResponseEntity.ok(rel);
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * POST /api/relatorio/horas/funcionario/pdf
     *
     * Relatório PDF de um único funcionário.
     */
    @PostMapping(value = "/funcionario/pdf", produces = "application/pdf")
    public ResponseEntity<?> funcionarioPdf(
            @RequestBody Map<String, String> body) {

        String pis = body.get("pis");
        String di  = body.get("dataInicial");
        String df  = body.get("dataFinal");

        if (pis == null || di == null || df == null) {
            return ResponseEntity.badRequest()
                    .body("Campos obrigatórios: pis, dataInicial, dataFinal");
        }

        try {
            RelatorioHorasFuncionarioDTO rel =
                    relatorioService.gerarPorPis(pis, di, df);
            byte[] pdf = pdfService.gerarFuncionario(rel, di, df);

            if (pdf == null) {
                return ResponseEntity.status(503)
                        .body("Erro ao gerar PDF");
            }

            String nomeArq = "Relatorio_"
                    + rel.getNome().replaceAll("\\s+", "_")
                    + "_" + di + ".pdf";

            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=\"" + nomeArq + "\"")
                    .header("Content-Type", "application/pdf")
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("erro", e.getMessage()));
        }
    }
}