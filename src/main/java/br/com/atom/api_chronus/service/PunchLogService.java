package br.com.atom.api_chronus.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.atom.api_chronus.config.IdClassConfig;
import br.com.atom.api_chronus.dto.AfdLineDTO;
import br.com.atom.api_chronus.dto.AfdResponseDTO;
import br.com.atom.api_chronus.dto.PunchLogDTO;
import br.com.atom.api_chronus.dto.PunchLogResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço para buscar e parsear registros de ponto do iDClass.
 *
 * Endpoint correto: POST /get_afd.fcgi?session=TOKEN
 *
 * IMPORTANTE:
 *   O iDClass exige o token na QUERY STRING para este endpoint.
 *   Usar apenas o Cookie header resulta em 401 Invalid session.
 *   Use authService.buildUrlComSession() que já monta a URL correta.
 *
 * O retorno é o AFD — texto posicional fixo, Portaria 671/INMETRO.
 * O AfdParserService converte cada linha em um objeto AfdLineDTO.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PunchLogService {

    private final IdClassConfig config;
    private final HttpClient httpClient;
    private final SessionManager sessionManager;
    private final IdClassAuthService authService;
    private final AfdParserService afdParser;

    /**
     * Busca o AFD completo do relógio e retorna as batidas parseadas.
     *
     * @param initialNsr NSR inicial (1 = do começo, null = do começo)
     * @return AfdResponseDTO com metadados e lista de batidas
     */
    public AfdResponseDTO buscarBatidas(Long initialNsr) {
        try {
            String session = sessionManager.getSessionValida();
            if (session == null) {
                log.error("Sem sessão válida para buscar AFD.");
                return null;
            }

            long nsr = (initialNsr != null && initialNsr > 0) ? initialNsr : 1;

            // Corpo da requisição: NSR inicial
            String body = String.format("{\"initial_nsr\":%d}", nsr);

            // URL com session na query string — obrigatório para get_afd.fcgi
            String url = authService.buildUrlComSession("/get_afd.fcgi", session);

            log.info("Buscando AFD. URL: {} | initial_nsr: {}", url, nsr);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                String conteudoAfd = response.body();
                log.info("AFD recebido. Tamanho: {} bytes.", conteudoAfd.length());

                // Parseia o texto AFD em objetos Java
                List<AfdLineDTO> batidas = afdParser.parsear(conteudoAfd);

                return new AfdResponseDTO(
                        conteudoAfd.split("\\r?\\n").length,
                        batidas.size(),
                        batidas
                );
            }

            // Sessão expirou — força renovação para o próximo request
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Sessão rejeitada (HTTP {}). Renovando token...",
                        response.statusCode());
                sessionManager.renovarTokenForcado();
            }

            log.error("Erro ao buscar AFD. HTTP {}: {}",
                    response.statusCode(), response.body());
            return null;

        } catch (Exception e) {
            log.error("Erro de comunicação com o relógio: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Busca batidas de um funcionário pelo PIS/CPF.
     * Faz a busca completa e filtra em memória.
     *
     * @param pis PIS ou CPF do funcionário (com ou sem zeros à esquerda)
     * @return lista de batidas do funcionário
     */
    public List<AfdLineDTO> buscarPorPis(String pis) {
        if (pis == null || pis.isBlank()) {
            return Collections.emptyList();
        }

        AfdResponseDTO resultado = buscarBatidas(1L);
        if (resultado == null || resultado.getBatidas() == null) {
            return Collections.emptyList();
        }

        // Normaliza o PIS para 12 chars com zeros à esquerda para comparar
        String pisNormalizado = String.format("%012d", Long.parseLong(pis.replaceAll("\\D", "")));

        return resultado.getBatidas().stream()
                .filter(b -> pisNormalizado.equals(b.getPis()))
                .toList();
    }
}