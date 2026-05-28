package br.com.atom.api_chronus.controller;

import br.com.atom.api_chronus.dto.AfdtRequestDTO;
import br.com.atom.api_chronus.service.AfdtGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Controller REST para geração do AFDT.
 *
 * Endpoints:
 *   POST /api/afdt              → gera AFDT completo
 *   POST /api/afdt/download     → gera e baixa como arquivo .txt
 */
@RestController
@RequestMapping("/api/afdt")
@RequiredArgsConstructor
public class AfdtController {

    private final AfdtGeneratorService afdtService;

    /**
     * POST /api/afdt
     *
     * Gera o AFDT e retorna como texto no body.
     * Útil para visualizar e testar.
     *
     * Body (opcional):
     * {
     *   "dataInicial": "01052026",
     *   "dataFinal":   "31052026",
     *   "pis":         "29525921620"
     * }
     *
     * 200: conteúdo AFDT como text/plain
     * 503: falha ao buscar batidas ou gerar arquivo
     */
    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> gerarAfdt(
            @RequestBody(required = false) AfdtRequestDTO request) {

        if (request == null) request = new AfdtRequestDTO();

        String conteudo = afdtService.gerar(request);

        if (conteudo != null) {
            return ResponseEntity.ok(conteudo);
        }

        return ResponseEntity
                .status(503)
                .body(Map.of("erro", "Não foi possível gerar o AFDT"));
    }

    /**
     * POST /api/afdt/download
     *
     * Gera o AFDT e retorna como download de arquivo .txt.
     * O nome do arquivo inclui a data de geração.
     *
     * 200: arquivo AFDT para download
     * 503: falha na geração
     */
    @PostMapping(value = "/download")
    public ResponseEntity<?> downloadAfdt(
            @RequestBody(required = false) AfdtRequestDTO request) {

        if (request == null) request = new AfdtRequestDTO();

        String conteudo = afdtService.gerar(request);

        if (conteudo == null) {
            return ResponseEntity
                    .status(503)
                    .body(Map.of("erro", "Não foi possível gerar o AFDT"));
        }

        // Nome do arquivo com timestamp
        String nomeArquivo = "AFDT_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                + ".txt";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(conteudo);
    }
}