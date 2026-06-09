package br.com.atom.api_chronus.controller;

import br.com.atom.api_chronus.dto.TratamentoPontoRequestDTO;
import br.com.atom.api_chronus.dto.TratamentoPontoResponseDTO;
import br.com.atom.api_chronus.service.TratamentoPontoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Controller REST para tratamentos de ponto.
 *
 * Endpoints:
 *   GET    /api/tratamentos/{pis}                     → lista por PIS
 *   GET    /api/tratamentos/{pis}/data/{data}          → por PIS + data
 *   GET    /api/tratamentos/{pis}/periodo              → por PIS + período
 *   GET    /api/tratamentos/id/{id}                   → por ID
 *   POST   /api/tratamentos                           → cria tratamento
 *   POST   /api/tratamentos/{id}/documento            → anexa documento
 *   GET    /api/tratamentos/{id}/documento            → baixa documento
 *   DELETE /api/tratamentos/{id}                      → exclui tratamento
 */
@RestController
@RequestMapping("/api/tratamentos")
@RequiredArgsConstructor
public class TratamentoPontoController {

    private final TratamentoPontoService service;

    // ── Consultas ─────────────────────────────────────────────────────────

    /**
     * GET /api/tratamentos/{pis}
     * Lista todos os tratamentos de um funcionário.
     */
    @GetMapping("/{pis}")
    public ResponseEntity<?> listarPorPis(@PathVariable String pis) {
        List<TratamentoPontoResponseDTO> lista =
                service.listarPorPis(pis);
        return lista.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(lista);
    }

    /**
     * GET /api/tratamentos/{pis}/data/{data}
     * Lista tratamentos de um funcionário em uma data específica.
     * Data no formato ddMMyyyy.
     */
    @GetMapping("/{pis}/data/{data}")
    public ResponseEntity<?> listarPorPisEData(
            @PathVariable String pis,
            @PathVariable String data) {
        try {
            List<TratamentoPontoResponseDTO> lista =
                    service.listarPorPisEData(pis, data);
            return lista.isEmpty()
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.ok(lista);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * GET /api/tratamentos/{pis}/periodo?di=ddMMyyyy&df=ddMMyyyy
     * Lista tratamentos de um funcionário em um período.
     */
    @GetMapping("/{pis}/periodo")
    public ResponseEntity<?> listarPorPeriodo(
            @PathVariable String pis,
            @RequestParam String di,
            @RequestParam String df) {
        try {
            List<TratamentoPontoResponseDTO> lista =
                    service.listarPorPisEPeriodo(pis, di, df);
            return lista.isEmpty()
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.ok(lista);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * GET /api/tratamentos/id/{id}
     * Busca tratamento por ID.
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        TratamentoPontoResponseDTO t = service.buscarPorId(id);
        return t != null
                ? ResponseEntity.ok(t)
                : ResponseEntity.status(404)
                        .body(Map.of("erro",
                                "Tratamento nao encontrado: " + id));
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /**
     * POST /api/tratamentos
     * Cria novo tratamento de ponto.
     *
     * Body:
     * {
     *   "pis":        "012952592162",
     *   "data":       "06052026",
     *   "horario":    "17:09",
     *   "ocorrencia": "D",
     *   "motivo":     "Registro extra por falha no sensor"
     * }
     *
     * Ocorrências:
     *   I = Horário incluído    (horario e motivo obrigatórios)
     *   D = Desconsiderado      (horario e motivo obrigatórios)
     *   P = Pré-assinalação     (horario e motivo opcionais)
     */
    @PostMapping
    public ResponseEntity<?> criar(
            @RequestBody TratamentoPontoRequestDTO req) {
        try {
            TratamentoPontoResponseDTO dto = service.criar(req);
            return ResponseEntity.status(201).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * POST /api/tratamentos/{id}/documento
     * Anexa documento comprovante ao tratamento.
     * Formatos aceitos: PDF, JPG, PNG.
     * Enviar como multipart/form-data com campo "arquivo".
     */
    @PostMapping(value = "/{id}/documento",
            consumes = "multipart/form-data")
    public ResponseEntity<?> anexarDocumento(
            @PathVariable Long id,
            @RequestParam("arquivo") MultipartFile arquivo) {
        try {
            TratamentoPontoResponseDTO dto =
                    service.anexarDocumento(id, arquivo);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("erro",
                            "Erro ao salvar documento: "
                            + e.getMessage()));
        }
    }

    /**
     * GET /api/tratamentos/{id}/documento
     * Baixa o documento comprovante do tratamento.
     */
    @GetMapping("/{id}/documento")
    public ResponseEntity<?> baixarDocumento(@PathVariable Long id) {
        try {
            byte[] conteudo = service.baixarDocumento(id);
            String tipo     = service.getTipoDocumento(id);
            String nome     = service.getNomeDocumento(id);

            return ResponseEntity.ok()
                    .header("Content-Type", tipo)
                    .header("Content-Disposition",
                            "inline; filename=\"" + nome + "\"")
                    .body(conteudo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("erro",
                            "Erro ao baixar documento: "
                            + e.getMessage()));
        }
    }

    /**
     * DELETE /api/tratamentos/{id}
     * Remove tratamento e seu documento do servidor.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluir(@PathVariable Long id) {
        try {
            service.excluir(id);
            return ResponseEntity.ok(
                    Map.of("mensagem",
                            "Tratamento " + id + " excluido."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("erro", e.getMessage()));
        }
    }
}