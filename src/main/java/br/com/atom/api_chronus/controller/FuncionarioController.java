package br.com.atom.api_chronus.controller;

import br.com.atom.api_chronus.dto.FuncionarioRequestDTO;
import br.com.atom.api_chronus.dto.FuncionarioResponseDTO;
import br.com.atom.api_chronus.service.FuncionarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller REST para CRUD de funcionários.
 *
 * Endpoints:
 *   GET    /api/funcionarios                        → lista todos ativos
 *   GET    /api/funcionarios/{pis}                  → busca por PIS
 *   GET    /api/funcionarios/nome/{nome}             → busca por nome
 *   GET    /api/funcionarios/supervisores            → lista supervisores
 *   GET    /api/funcionarios/supervisores/setor/{s} → supervisores por setor
 *   GET    /api/funcionarios/{pis}/subordinados      → subordinados
 *   POST   /api/funcionarios                        → cria
 *   PUT    /api/funcionarios/{pis}                  → atualiza
 *   DELETE /api/funcionarios/{pis}                  → inativa
 *   PATCH  /api/funcionarios/{pis}/reativar         → reativa
 */
@RestController
@RequestMapping("/api/funcionarios")
@RequiredArgsConstructor
public class FuncionarioController {

    private final FuncionarioService service;

    // ── Consultas ─────────────────────────────────────────────────────────

    /** GET /api/funcionarios — lista todos os ativos */
    @GetMapping
    public ResponseEntity<List<FuncionarioResponseDTO>> listar() {
        List<FuncionarioResponseDTO> lista = service.listar();
        return lista.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(lista);
    }

    /** GET /api/funcionarios/{pis} — busca por PIS */
    @GetMapping("/{pis}")
    public ResponseEntity<?> buscarPorPis(@PathVariable String pis) {
        FuncionarioResponseDTO f = service.buscarPorPis(pis);
        return f != null
                ? ResponseEntity.ok(f)
                : ResponseEntity.status(404).body(
                        Map.of("erro",
                                "Funcionario nao encontrado: " + pis));
    }

    /** GET /api/funcionarios/nome/{nome} — busca por nome parcial */
    @GetMapping("/nome/{nome}")
    public ResponseEntity<?> buscarPorNome(@PathVariable String nome) {
        List<FuncionarioResponseDTO> lista = service.buscarPorNome(nome);
        return lista.isEmpty()
                ? ResponseEntity.status(404).body(
                        Map.of("erro",
                                "Nenhum funcionario encontrado: " + nome))
                : ResponseEntity.ok(lista);
    }

    /** GET /api/funcionarios/supervisores — lista todos os supervisores */
    @GetMapping("/supervisores")
    public ResponseEntity<?> listarSupervisores() {
        List<FuncionarioResponseDTO> lista = service.listarSupervisores();
        return lista.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(lista);
    }

    /**
     * GET /api/funcionarios/supervisores/setor/{setor}
     * Lista supervisores de um setor específico.
     */
    @GetMapping("/supervisores/setor/{setor}")
    public ResponseEntity<?> supervisoresPorSetor(
            @PathVariable String setor) {
        List<FuncionarioResponseDTO> lista =
                service.listarSupervisoresPorSetor(setor);
        return lista.isEmpty()
                ? ResponseEntity.status(404).body(
                        Map.of("erro",
                                "Nenhum supervisor no setor: " + setor))
                : ResponseEntity.ok(lista);
    }

    /**
     * GET /api/funcionarios/{pis}/subordinados
     * Lista todos os subordinados de um supervisor.
     */
    @GetMapping("/{pis}/subordinados")
    public ResponseEntity<?> listarSubordinados(
            @PathVariable String pis) {
        try {
            List<FuncionarioResponseDTO> lista =
                    service.listarSubordinados(pis);
            return lista.isEmpty()
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.ok(lista);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /**
     * POST /api/funcionarios — cria novo funcionário
     *
     * Body mínimo:  { "pis": "...", "nome": "..." }
     * Body completo:
     * {
     *   "pis":           "012345678901",
     *   "nome":          "FULANO DE TAL",
     *   "matricula":     100,
     *   "cpf":           "12345678901",
     *   "rg":            "12.345.678-9",
     *   "endereco":      "Rua Exemplo, 100",
     *   "cargo":         "Auxiliar",
     *   "setor":         "Administrativo",
     *   "email":         "fulano@empresa.com",
     *   "celular":       "(14) 99999-9999",
     *   "salario":       2500.00,
     *   "supervisor":    false,
     *   "supervisorPis": "012952592162",
     *   "dataAdmissao":  "01012024",
     *   "observacoes":   "Contrato temporário"
     * }
     */
    @PostMapping
    public ResponseEntity<?> criar(
            @RequestBody FuncionarioRequestDTO req) {

        if (req.getPis() == null || req.getPis().isBlank()
                || req.getNome() == null || req.getNome().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro",
                            "Campos obrigatorios: pis, nome"));
        }

        try {
            return ResponseEntity.status(201).body(service.criar(req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /** PUT /api/funcionarios/{pis} — atualiza funcionário */
    @PutMapping("/{pis}")
    public ResponseEntity<?> atualizar(
            @PathVariable String pis,
            @RequestBody FuncionarioRequestDTO req) {

        if (req.getNome() == null || req.getNome().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", "Campo obrigatorio: nome"));
        }

        try {
            return ResponseEntity.ok(service.atualizar(pis, req));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /** DELETE /api/funcionarios/{pis} — inativa (soft delete) */
    @DeleteMapping("/{pis}")
    public ResponseEntity<?> inativar(@PathVariable String pis) {
        try {
            return ResponseEntity.ok(service.inativar(pis));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("erro", e.getMessage()));
        }
    }

    /** PATCH /api/funcionarios/{pis}/reativar — reativa inativo */
    @PatchMapping("/{pis}/reativar")
    public ResponseEntity<?> reativar(@PathVariable String pis) {
        try {
            return ResponseEntity.ok(service.reativar(pis));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("erro", e.getMessage()));
        }
    }
}