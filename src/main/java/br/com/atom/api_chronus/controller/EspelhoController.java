package br.com.atom.api_chronus.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.api_chronus.dto.EspelhoRequestDTO;
import br.com.atom.api_chronus.dto.EspelhoResponseDTO;
import br.com.atom.api_chronus.dto.UsuarioDTO;
import br.com.atom.api_chronus.service.EspelhoService;
import br.com.atom.api_chronus.service.UsuarioService;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para o Espelho de Ponto.
 *
 * Endpoints:
 *   POST /api/espelho/pis    → espelho por PIS
 *   POST /api/espelho/nome   → espelho por nome (Passo A.2)
 *   POST /api/espelho/codigo → espelho por código (Passo A.3)
 */
@RestController
@RequestMapping("/api/espelho")
@RequiredArgsConstructor
public class EspelhoController {

    private final EspelhoService espelhoService;
    private final UsuarioService usuarioService;


    /**
     * POST /api/espelho/pis
     *
     * Gera o espelho de ponto de um funcionário pelo PIS.
     *
     * Body:
     * {
     *   "pis":         "012952592162",
     *   "dataInicial": "01052026",
     *   "dataFinal":   "31052026"
     * }
     *
     * 200: EspelhoResponseDTO completo
     * 400: parâmetros inválidos
     * 503: falha ao buscar dados do relógio
     */
    @PostMapping("/pis")
    public ResponseEntity<?> espelhoPorPis(
            @RequestBody EspelhoRequestDTO request) {

        // Valida campos obrigatórios
        if (request.getPis() == null || request.getPis().isBlank()
                || request.getDataInicial() == null
                || request.getDataFinal() == null) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("erro",
                            "Campos obrigatórios: pis, dataInicial, dataFinal"));
        }

        EspelhoResponseDTO espelho = espelhoService.gerar(request);

        if (espelho != null) {
            return ResponseEntity.ok(espelho);
        }

        return ResponseEntity
                .status(503)
                .body(Map.of("erro",
                        "Não foi possível gerar o espelho de ponto"));
    }

    

    /**
 * POST /api/espelho/nome
 *
 * Gera o espelho de ponto buscando o funcionário pelo nome.
 * A busca é case-insensitive e aceita parte do nome.
 *
 * Body:
 * {
 *   "nome":        "DONATA",
 *   "dataInicial": "01052026",
 *   "dataFinal":   "31052026"
 * }
 *
 * 200: EspelhoResponseDTO completo
 * 404: funcionário não encontrado
 * 400: parâmetros inválidos
 */
@PostMapping("/nome")
public ResponseEntity<?> espelhoPorNome(
        @RequestBody Map<String, String> body) {

    String nome        = body.get("nome");
    String dataInicial = body.get("dataInicial");
    String dataFinal   = body.get("dataFinal");

    if (nome == null || nome.isBlank()
            || dataInicial == null || dataFinal == null) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("erro",
                        "Campos obrigatórios: nome, dataInicial, dataFinal"));
    }

    // Busca o usuário pelo nome no relógio
    UsuarioDTO usuario = usuarioService.buscarPorNome(nome);
    if (usuario == null) {
        return ResponseEntity
                .status(404)
                .body(Map.of("erro",
                        "Funcionário não encontrado com o nome: " + nome));
    }

    // Reutiliza o mesmo serviço do espelho por PIS
    EspelhoRequestDTO request = new EspelhoRequestDTO();
    request.setPis(usuario.getPisFormatado());
    request.setDataInicial(dataInicial);
    request.setDataFinal(dataFinal);

    EspelhoResponseDTO espelho = espelhoService.gerar(request);

    if (espelho != null) {
        return ResponseEntity.ok(espelho);
    }

    return ResponseEntity
            .status(503)
            .body(Map.of("erro", "Não foi possível gerar o espelho de ponto"));
}

/**
 * POST /api/espelho/codigo
 *
 * Gera o espelho de ponto buscando o funcionário pelo código de matrícula.
 *
 * Body:
 * {
 *   "codigo":      95,
 *   "dataInicial": "01052026",
 *   "dataFinal":   "31052026"
 * }
 *
 * 200: EspelhoResponseDTO completo
 * 404: funcionário não encontrado
 * 400: parâmetros inválidos
 */
@PostMapping("/codigo")
public ResponseEntity<?> espelhoPorCodigo(
        @RequestBody Map<String, String> body) {

    String codigoStr   = body.get("codigo");
    String dataInicial = body.get("dataInicial");
    String dataFinal   = body.get("dataFinal");

    if (codigoStr == null || codigoStr.isBlank()
            || dataInicial == null || dataFinal == null) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("erro",
                        "Campos obrigatórios: codigo, dataInicial, dataFinal"));
    }

    int codigo;
    try {
        codigo = Integer.parseInt(codigoStr);
    } catch (NumberFormatException e) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("erro", "Código deve ser numérico"));
    }

    // Busca o usuário pelo código de matrícula
    UsuarioDTO usuario = usuarioService.buscarPorCodigo(codigo);
    if (usuario == null) {
        return ResponseEntity
                .status(404)
                .body(Map.of("erro",
                        "Funcionário não encontrado com o código: " + codigo));
    }

    EspelhoRequestDTO request = new EspelhoRequestDTO();
    request.setPis(usuario.getPisFormatado());
    request.setDataInicial(dataInicial);
    request.setDataFinal(dataFinal);

    EspelhoResponseDTO espelho = espelhoService.gerar(request);

    if (espelho != null) {
        return ResponseEntity.ok(espelho);
    }

    return ResponseEntity
            .status(503)
            .body(Map.of("erro", "Não foi possível gerar o espelho de ponto"));
}
}