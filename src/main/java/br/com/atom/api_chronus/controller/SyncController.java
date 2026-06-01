package br.com.atom.api_chronus.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.api_chronus.entity.LogSincronizacao;
import br.com.atom.api_chronus.repository.LogSincronizacaoRepository;
import br.com.atom.api_chronus.service.SyncService;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para sincronização manual com o relógio.
 *
 * Endpoints:
 *   POST /api/sync/completo  → sincroniza usuários + batidas
 *   POST /api/sync/batidas   → sincroniza somente batidas (incremental)
 *   POST /api/sync/usuarios  → sincroniza somente usuários
 *   GET  /api/sync/logs      → histórico das últimas sincronizações
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService               syncService;
    private final LogSincronizacaoRepository logRepo;

    /**
     * POST /api/sync/completo
     * Sincroniza usuários + batidas de ponto.
     */
    @PostMapping("/completo")
    public ResponseEntity<LogSincronizacao> sincronizarCompleto() {
        return ResponseEntity.ok(syncService.sincronizarCompleto());
    }

    /**
     * POST /api/sync/batidas
     * Sincroniza somente batidas (incremental a partir do último NSR).
     */
    @PostMapping("/batidas")
    public ResponseEntity<LogSincronizacao> sincronizarBatidas() {
        return ResponseEntity.ok(syncService.sincronizarBatidas());
    }

    /**
     * POST /api/sync/usuarios
     * Sincroniza somente os usuários do relógio.
     */
    @PostMapping("/usuarios")
    public ResponseEntity<LogSincronizacao> sincronizarUsuarios() {
        return ResponseEntity.ok(syncService.sincronizarUsuarios());
    }

    /**
     * GET /api/sync/logs
     * Retorna as últimas 20 sincronizações realizadas.
     */
    @GetMapping("/logs")
    public ResponseEntity<List<LogSincronizacao>> logs() {
        return ResponseEntity.ok(logRepo.findTop20ByOrderByDataInicioDesc());
    }
}