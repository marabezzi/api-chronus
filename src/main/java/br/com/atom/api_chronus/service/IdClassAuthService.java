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
 * O iDClass suporta dois modos de autenticação:
 *
 *   1. Cookie header:   Cookie: session=TOKEN
 *      → usado em: login, session_is_valid, maioria dos endpoints
 *
 *   2. Query string:    /endpoint.fcgi?session=TOKEN
 *      → obrigatório em: get_afd.fcgi e possivelmente outros
 *
 * Ambos os métodos são fornecidos por este serviço para uso nos services.
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
     * Monta o header Cookie para endpoints que usam autenticação por cookie.
     *
     * Uso:
     *   request.header("Cookie", authService.buildCookie(session))
     *
     * @param sessionToken token de sessão
     * @return "session=TOKEN"
     */
    public String buildCookie(String sessionToken) {
        return "session=" + sessionToken;
    }

    /**
     * Monta a URL completa com o session na query string.
     * Obrigatório para: get_afd.fcgi e endpoints que não aceitam cookie.
     *
     * Uso:
     *   String url = authService.buildUrlComSession("/get_afd.fcgi", session);
     *
     * @param endpoint ex: "/get_afd.fcgi"
     * @param sessionToken token de sessão
     * @return ex: "https://192.168.1.201:443/get_afd.fcgi?session=TOKEN"
     */
    public String buildUrlComSession(String endpoint, String sessionToken) {
        return config.getBaseUrl() + endpoint + "?session=" + sessionToken;
    }
}