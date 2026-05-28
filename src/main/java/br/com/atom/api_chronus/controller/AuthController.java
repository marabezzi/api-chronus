package br.com.atom.api_chronus.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.api_chronus.dto.LoginResponseDTO;
import br.com.atom.api_chronus.dto.SessionStatusDTO;
import br.com.atom.api_chronus.service.IdClassAuthService;
import br.com.atom.api_chronus.service.SessionManager;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para autenticação e status de sessão.
 *
 * Endpoints:
 *   POST /api/auth/login    — faz login e retorna token (uso manual/teste)
 *   GET  /api/auth/status   — informa estado da sessão atual
 *   POST /api/auth/renovar  — força renovação do token
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SessionManager sessionManager;

    /**
     * POST /api/auth/login
     *
     * Obtém uma sessão válida (reutiliza se ainda não expirou).
     * Em produção, os services chamam o SessionManager diretamente.
     * Este endpoint existe para testes manuais e diagnóstico.
     *
     * 200: { "session": "abc123..." }
     * 503: { "erro": "Não foi possível conectar ao relógio iDClass" }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login() {

        // Usa o SessionManager — reutiliza token se válido, renova se expirou
        String session = sessionManager.getSessionValida();

        if (session != null) {
            return ResponseEntity.ok(new LoginResponseDTO(session));
        }

        return ResponseEntity
                .status(503)
                .body(Map.of("erro", "Não foi possível conectar ao relógio iDClass"));
    }

    /**
     * GET /api/auth/status
     *
     * Retorna o estado atual da sessão com o relógio.
     * Útil para monitoramento e debug sem expor o token.
     *
     * 200: { "sessaoAtiva": true, "segundosRestantes": 342, "mensagem": "..." }
     */
    @GetMapping("/status")
    public ResponseEntity<SessionStatusDTO> status() {

        boolean ativa = sessionManager.temSessaoAtiva();
        long segundos = sessionManager.getSegundosRestantes();

        String mensagem = ativa
                ? String.format("Sessão ativa — expira em %d segundos", segundos)
                : "Sem sessão ativa — próximo request fará login automaticamente";

        return ResponseEntity.ok(new SessionStatusDTO(ativa, segundos, mensagem));
    }

    /**
     * POST /api/auth/renovar
     *
     * Força a renovação do token mesmo que ainda seja válido.
     * Use quando suspeitar que o relógio invalidou a sessão.
     *
     * 200: { "session": "novoToken..." }
     * 503: { "erro": "..." }
     */
    @PostMapping("/renovar")
    public ResponseEntity<?> renovar() {

        String session = sessionManager.renovarTokenForcado();

        if (session != null) {
            return ResponseEntity.ok(new LoginResponseDTO(session));
        }

        return ResponseEntity
                .status(503)
                .body(Map.of("erro", "Falha ao renovar sessão com o relógio"));
    }
}