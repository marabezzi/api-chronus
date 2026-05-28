package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.config.IdClassConfig;
import br.com.atom.api_chronus.dto.LoginRequestDTO;
import br.com.atom.api_chronus.dto.LoginResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
/**
 * Serviço de autenticação com o relógio iDClass.
 *
 * Passo 1: login()        — faz o POST /login.fcgi e retorna o token
 * Passo 2: buildCookie()  — monta o header Cookie para uso nos requests
 *
 * Separação de responsabilidades:
 *   - IdClassAuthService → sabe como fazer login no relógio
 *   - SessionManager     → decide quando fazer login (gerência do TTL)
 *   - Demais services    → usam getSessionValida() sem saber dos detalhes
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdClassAuthService {

    private final IdClassConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Realiza o login no relógio iDClass.
     *
     * @return LoginResponseDTO com o token de sessão, ou null se falhar
     */
    public LoginResponseDTO login() {
        try {
            // Monta o corpo: {"login":"admin","password":"admin"}
            LoginRequestDTO loginRequest = new LoginRequestDTO(
                    config.getUser(),
                    config.getPassword()
            );
            String requestBody = objectMapper.writeValueAsString(loginRequest);

            String url = config.getBaseUrl() + "/login.fcgi";
            log.debug("Tentando login no relógio: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                LoginResponseDTO loginResponse = objectMapper.readValue(
                        response.body(),
                        LoginResponseDTO.class
                );
                log.info("Login realizado com sucesso.");
                return loginResponse;
            }

            log.error("Falha no login. HTTP {}: {}", response.statusCode(), response.body());
            return null;

        } catch (Exception e) {
            log.error("Erro ao conectar com o relógio iDClass: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Monta o valor do header Cookie para os requests ao relógio.
     *
     * O iDClass exige que o token de sessão seja enviado como cookie:
     *   Cookie: session=xYz9AbC...
     *
     * Uso nos próximos services:
     *   String cookie = authService.buildCookie(sessionManager.getSessionValida());
     *   HttpRequest.newBuilder().header("Cookie", cookie)...
     *
     * @param sessionToken token retornado pelo login
     * @return string formatada para o header Cookie
     */
    public String buildCookie(String sessionToken) {
        return "session=" + sessionToken;
    }
}