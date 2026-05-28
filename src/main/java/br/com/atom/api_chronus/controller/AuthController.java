package br.com.atom.api_chronus.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.api_chronus.dto.LoginResponseDTO;
import br.com.atom.api_chronus.service.IdClassAuthService;
import lombok.RequiredArgsConstructor;

/**
* Controller REST para operações de autenticação no Chronus.
*
* Responsabilidade única: receber requisições HTTP, delegar ao service
* e devolver a resposta adequada. Nenhuma lógica de negócio aqui.
*
* Boas práticas:
* - @RequiredArgsConstructor: injeção imutável via construtor
* - ResponseEntity<?> como retorno: controle total do status HTTP
* - Map.of() para erros: garante Content-Type application/json consistente
* (evita retornar String JSON crua, que pode ter Content-Type text/plain)
* - Sem @Autowired: injeção via construtor é a forma recomendada pelo Spring
*/
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {


    /** Serviço que implementa a lógica de login no relógio */
    private final IdClassAuthService authService;
    /**
    * POST /api/auth/login
    *
    * Autentica no relógio iDClass e retorna o token de sessão.
    *
    * Respostas:
    * 200 OK: { "session": "abc123..." }
    * 503 Service Unavailable: { "erro": "Não foi possível conectar..." }
    *
    * O token "session" deve ser usado como cookie nos próximos requests:
    * Cookie: session=abc123...
    */
    @PostMapping("/login")
    public ResponseEntity<?> login() {
        // Delega a lógica ao service — controller não sabe como o login funciona
    LoginResponseDTO response = authService.login();
    // Verifica se obteve sessão válida
    if (response != null && response.getSession() != null) {
    // 200 OK com o token de sessão
    return ResponseEntity.ok(response);
    }
    // 503: relógio indisponível ou credenciais incorretas
    // Map.of() serializado pelo Jackson ® { "erro": "..." }
    return ResponseEntity
    .status(503)
    .body(Map.of("erro", "Não foi possível conectar ao relógio iDClass"));
    }

}
