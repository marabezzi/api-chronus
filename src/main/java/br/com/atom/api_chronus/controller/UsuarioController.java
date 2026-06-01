package br.com.atom.api_chronus.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.api_chronus.dto.UsuarioDTO;
import br.com.atom.api_chronus.service.UsuarioService;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para usuários cadastrados no relógio iDClass.
 *
 * Endpoints:
 *   GET /api/usuarios        → lista todos os usuários
 *   GET /api/usuarios/{pis}  → busca usuário pelo PIS
 *   GET /api/usuarios/nome/{nome} → busca usuário pelo nome
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    /**
     * GET /api/usuarios
     *
     * Lista todos os usuários cadastrados no relógio.
     *
     * 200: [ { "name": "...", "pis": 12849194257, ... } ]
     * 204: nenhum usuário encontrado
     */
    @GetMapping
    public ResponseEntity<List<UsuarioDTO>> listarTodos() {
        List<UsuarioDTO> usuarios = usuarioService.listarTodos();

        if (usuarios.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(usuarios);
    }

    /**
     * GET /api/usuarios/{pis}
     *
     * Busca um usuário pelo PIS.
     * Aceita com ou sem zeros à esquerda.
     *
     * 200: { "name": "PATRICIA APARECIDA ALVES", "pis": 12849194257, ... }
     * 404: usuário não encontrado
     */
    @GetMapping("/{pis}")
    public ResponseEntity<?> buscarPorPis(@PathVariable String pis) {
        UsuarioDTO usuario = usuarioService.buscarPorPis(pis);

        if (usuario != null) {
            return ResponseEntity.ok(usuario);
        }

        return ResponseEntity
                .status(404)
                .body(Map.of("erro", "Usuário não encontrado para o PIS: " + pis));
    }

    /**
     * GET /api/usuarios/nome/{nome}
     *
     * Busca usuário pelo nome (parcial, case-insensitive).
     * Exemplo: /api/usuarios/nome/DONATA
     *
     * 200: { "name": "DONATA APARECIDA MARTINS GARCIA", ... }
     * 404: não encontrado
     */
    @GetMapping("/nome/{nome}")
    public ResponseEntity<?> buscarPorNome(@PathVariable String nome) {
        UsuarioDTO usuario = usuarioService.buscarPorNome(nome);

        if (usuario != null) {
            return ResponseEntity.ok(usuario);
        }

        return ResponseEntity
                .status(404)
                .body(Map.of("erro", "Usuário não encontrado com o nome: " + nome));
    }
}