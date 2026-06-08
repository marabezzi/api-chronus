package br.com.atom.api_chronus.controller;

import br.com.atom.api_chronus.dto.EspelhoRequestDTO;
import br.com.atom.api_chronus.dto.EspelhoResponseDTO;
import br.com.atom.api_chronus.dto.UsuarioDTO;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import br.com.atom.api_chronus.repository.UsuarioPontoRepository;
import br.com.atom.api_chronus.service.EspelhoMtePdfService;
import br.com.atom.api_chronus.service.EspelhoService;
import br.com.atom.api_chronus.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para Espelho de Ponto fiel ao Anexo II
 * da Portaria 1510/MTE.
 *
 * Endpoints:
 *   POST /api/mte/espelho/pis        → JSON por PIS
 *   POST /api/mte/espelho/nome       → JSON por nome
 *   POST /api/mte/espelho/pis/pdf    → PDF MTE por PIS
 *   POST /api/mte/espelho/nome/pdf   → PDF MTE por nome
 */
@RestController
@RequestMapping("/api/mte/espelho")
@RequiredArgsConstructor
public class EspelhoMteController {

private final EspelhoService       espelhoService;
private final EspelhoMtePdfService pdfService;
private final UsuarioService       usuarioService;
private final UsuarioPontoRepository usuarioRepo; // ← adicione

    /**
     * POST /api/mte/espelho/pis
     * JSON do espelho por PIS.
     */
    @PostMapping("/pis")
    public ResponseEntity<?> jsonPorPis(
            @RequestBody EspelhoRequestDTO request) {

        if (request.getPis() == null || request.getPis().isBlank()
                || request.getDataInicial() == null
                || request.getDataFinal() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro",
                            "Campos obrigatorios: pis, dataInicial, dataFinal"));
        }

        EspelhoResponseDTO espelho = espelhoService.gerar(request);
        return espelho != null
                ? ResponseEntity.ok(espelho)
                : ResponseEntity.status(503)
                        .body(Map.of("erro",
                                "Nao foi possivel gerar o espelho"));
    }

    /**
     * POST /api/mte/espelho/nome
     * JSON do espelho por nome.
     */
    @PostMapping("/nome")
    public ResponseEntity<?> jsonPorNome(
            @RequestBody Map<String, String> body) {

        String nome = body.get("nome");
        String di   = body.get("dataInicial");
        String df   = body.get("dataFinal");

        if (nome == null || di == null || df == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro",
                            "Campos obrigatorios: nome, dataInicial, dataFinal"));
        }

        UsuarioDTO usuario = usuarioService.buscarPorNome(nome);
        if (usuario == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("erro",
                            "Funcionario nao encontrado: " + nome));
        }

        EspelhoRequestDTO req = new EspelhoRequestDTO();
        req.setPis(usuario.getPisFormatado());
        req.setDataInicial(di);
        req.setDataFinal(df);

        EspelhoResponseDTO espelho = espelhoService.gerar(req);
        return espelho != null
                ? ResponseEntity.ok(espelho)
                : ResponseEntity.status(503)
                        .body(Map.of("erro",
                                "Nao foi possivel gerar o espelho"));
    }

    /**
     * POST /api/mte/espelho/pis/pdf
     * PDF fiel ao Anexo II da Portaria 1510/MTE — por PIS.
     */
    @PostMapping(value = "/pis/pdf", produces = "application/pdf")
    public ResponseEntity<?> pdfPorPis(
            @RequestBody EspelhoRequestDTO request) {

        if (request.getPis() == null || request.getPis().isBlank()
                || request.getDataInicial() == null
                || request.getDataFinal() == null) {
            return ResponseEntity.badRequest()
                    .body("Campos obrigatorios: pis, dataInicial, dataFinal");
        }

        EspelhoResponseDTO espelho = espelhoService.gerar(request);
        if (espelho == null) {
            return ResponseEntity.status(503)
                    .body("Nao foi possivel gerar o espelho");
        }

        byte[] pdf = pdfService.gerar(espelho, request.getDataInicial(),
                request.getDataFinal());
        if (pdf == null) {
            return ResponseEntity.status(503)
                    .body("Erro ao gerar PDF MTE");
        }

        String nomeArq = "EspelhoMTE_"
                + espelho.getNome().replaceAll("\\s+", "_")
                + "_" + request.getDataInicial() + ".pdf";

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + nomeArq + "\"")
                .header("Content-Type", "application/pdf")
                .body(pdf);
    }

    /**
     * POST /api/mte/espelho/nome/pdf
     * PDF fiel ao Anexo II da Portaria 1510/MTE — por nome.
     */
    @PostMapping(value = "/nome/pdf", produces = "application/pdf")
    public ResponseEntity<?> pdfPorNome(
            @RequestBody Map<String, String> body) {

        String nome = body.get("nome");
        String di   = body.get("dataInicial");
        String df   = body.get("dataFinal");

        if (nome == null || di == null || df == null) {
            return ResponseEntity.badRequest()
                    .body("Campos obrigatorios: nome, dataInicial, dataFinal");
        }

        UsuarioDTO usuario = usuarioService.buscarPorNome(nome);
        if (usuario == null) {
            return ResponseEntity.status(404)
                    .body("Funcionario nao encontrado: " + nome);
        }

        EspelhoRequestDTO req = new EspelhoRequestDTO();
        req.setPis(usuario.getPisFormatado());
        req.setDataInicial(di);
        req.setDataFinal(df);

        EspelhoResponseDTO espelho = espelhoService.gerar(req);
        if (espelho == null) {
            return ResponseEntity.status(503)
                    .body("Nao foi possivel gerar o espelho");
        }

        byte[] pdf = pdfService.gerar(espelho, di, df);
        if (pdf == null) {
            return ResponseEntity.status(503)
                    .body("Erro ao gerar PDF MTE");
        }

        String nomeArq = "EspelhoMTE_"
                + espelho.getNome().replaceAll("\\s+", "_")
                + "_" + di + ".pdf";

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + nomeArq + "\"")
                .header("Content-Type", "application/pdf")
                .body(pdf);
    }

     /**
         * POST /api/mte/espelho/todos/pdf
         *
         * Gera um PDF único com o espelho de ponto de TODOS os
         * funcionários no período informado.
         * Funcionários sem ponto no período são ignorados.
         *
         * Body: { "dataInicial": "01052026", "dataFinal": "31052026" }
         */
        @PostMapping(value = "/todos/pdf", produces = "application/pdf")
        public ResponseEntity<?> pdfTodos(
                @RequestBody Map<String, String> body) {

        String di = body.get("dataInicial");
        String df = body.get("dataFinal");

        if (di == null || df == null) {
                return ResponseEntity.badRequest()
                        .body("Campos obrigatorios: dataInicial, dataFinal");
        }

        // Busca todos os funcionários ativos
        List<br.com.atom.api_chronus.entity.UsuarioPonto> usuarios =
                usuarioRepo.findByAtivoTrueOrderByNameAsc();

        if (usuarios.isEmpty()) {
                return ResponseEntity.noContent().build();
        }

        // Gera o espelho de cada funcionário
        List<EspelhoResponseDTO> espelhos = usuarios.stream()
                .map(u -> {
                        EspelhoRequestDTO req = new EspelhoRequestDTO();
                        req.setPis(u.getPisFormatado());
                        req.setDataInicial(di);
                        req.setDataFinal(df);
                        return espelhoService.gerar(req);
                })
                .filter(e -> e != null
                        && e.getDias() != null
                        && !e.getDias().isEmpty())
                .collect(java.util.stream.Collectors.toList());

        if (espelhos.isEmpty()) {
                return ResponseEntity.status(404)
                        .body("Nenhum funcionario com ponto no periodo "
                                + di + " a " + df);
        }

        // Gera PDF consolidado
        byte[] pdf = pdfService.gerarTodos(espelhos, di, df);
        if (pdf == null) {
                return ResponseEntity.status(503)
                        .body("Erro ao gerar PDF consolidado");
        }

        String nomeArq = "EspelhoMTE_Todos_" + di + "_" + df + ".pdf";

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"" + nomeArq + "\"")
                .header("Content-Type", "application/pdf")
                .body(pdf);
        }

}