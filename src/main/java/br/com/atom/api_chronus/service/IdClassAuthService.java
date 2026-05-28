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
* Fluxo de login:
* 1. Monta JSON: { "login": "...", "password": "..." }
* 2. POST https://{host}:{port}/login.fcgi
* 3. Recebe { "session": "token..." }
* 4. Retorna o token para uso nos próximos requests
*
* Boas práticas aplicadas:
* - @Slf4j (Lombok): injeta logger sem instanciar manualmente
* - @RequiredArgsConstructor (Lombok): injeção via construtor (imutável e testável)
* - ObjectMapper instanciado localmente — em projetos maiores, injete como @Bean
* - Tratamento de erro explícito com log descritivo
* - Retorna null em falha — o controller decide o que responder ao cliente
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class IdClassAuthService {

    /** Configurações do relógio (host, port, user, password) */
    private final IdClassConfig config;
    /** HttpClient com SSL configurado para aceitar certificado auto-assinado */
    private final HttpClient httpClient;
    /** Serializa objetos Java para JSON e vice-versa */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
    * Realiza o login no relógio iDClass.
    *
    * @return LoginResponseDTO com o token de sessão se bem-sucedido,
    * ou null se o login falhar ou houver erro de comunicação.
    */
    public LoginResponseDTO login() {
        try {
        // Passo 1: monta o corpo da requisição 
        // LoginRequestDTO usa os valores lidos do .env via IdClassConfig
        LoginRequestDTO loginRequest = new LoginRequestDTO(
        config.getUser(),
        config.getPassword()
        );


        // Passo 2: serializa para JSON 
        // Resultado: {"login":"admin","password":"admin"}
        String requestBody = objectMapper.writeValueAsString(loginRequest);

        //  Passo 3: monta a URL do endpoint de login 
        // Ex: https://192.168.0.1:443/login.fcgi
        String url = config.getBaseUrl() + "/login.fcgi";
        log.debug("Tentando login no relógio: {}", url);

        //  Passo 4: cria a requisição HTTP POST
        // iDClass exige: POST, Content-Type: application/json, corpo UTF-8
        HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

        // Passo 5: envia e aguarda resposta (bloqueante)
        HttpResponse<String> response = httpClient.send(
        request,
        HttpResponse.BodyHandlers.ofString()
        );

        //  Passo 6: valida o status HTTP
        if (response.statusCode() == 200) {

        // Passo 7: desserializa o JSON de resposta
        // {"session":"abc123..."} -> LoginResponseDTO
        LoginResponseDTO loginResponse = objectMapper.readValue(
        response.body(),
        LoginResponseDTO.class
        );
        log.info("Login no relógio realizado com sucesso. Session: {}",
        loginResponse.getSession());
        return loginResponse;

        }
        // Login recusado pelo relógio (credenciais erradas, etc.)
        log.error("Falha no login. HTTP {}: {}", response.statusCode(), response.body());
        return null;
        } catch (Exception e) {
        // Erro de rede, timeout, SSL, serialização, etc.
        log.error("Erro ao conectar com o relógio iDClass: {}", e.getMessage(), e);
        return null;
        }
        }
}
