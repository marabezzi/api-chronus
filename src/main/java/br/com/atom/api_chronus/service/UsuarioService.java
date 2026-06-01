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
import br.com.atom.api_chronus.dto.UsuarioDTO;
import br.com.atom.api_chronus.dto.UsuarioResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço para listar usuários cadastrados no relógio iDClass.
 *
 * Endpoint: POST /load_users.fcgi
 * Autenticação: Cookie: session={token}
 *
 * Parâmetros obrigatórios:
 *   limit            (int)     → máximo de registros por página (máx ~50)
 *   offset           (int)     → índice de início para paginação
 *   include_templates(boolean) → false = não retorna dados biométricos
 *
 * O relógio retorna no máximo ~50 usuários por chamada.
 * O service pagina automaticamente até buscar todos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final IdClassConfig config;
    private final HttpClient httpClient;
    private final SessionManager sessionManager;
    private final IdClassAuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Registros por página — valor seguro para o iDClass */
    private static final int PAGE_SIZE = 50;

    /**
     * Busca todos os usuários cadastrados no relógio.
     * Pagina automaticamente até esgotar os registros.
     *
     * @return lista completa de usuários, ou lista vazia se houver erro
     */
    public List<UsuarioDTO> listarTodos() {
        List<UsuarioDTO> todos   = new ArrayList<>();
        int              offset  = 0;
        int              pagina  = 1;

        log.info("Buscando usuários do relógio...");

        while (true) {
            List<UsuarioDTO> pagina_resultado = buscarPagina(offset);

            if (pagina_resultado == null) {
                log.warn("Falha na página {}. Retornando {} usuários coletados.",
                        pagina, todos.size());
                break;
            }

            todos.addAll(pagina_resultado);
            log.debug("Página {} — {} usuários. Total: {}",
                    pagina, pagina_resultado.size(), todos.size());

            // Se retornou menos que o tamanho da página, chegou ao fim
            if (pagina_resultado.size() < PAGE_SIZE) {
                break;
            }

            offset += PAGE_SIZE;
            pagina++;
        }

        log.info("Total de usuários encontrados: {}", todos.size());
        return todos;
    }

    /**
     * Busca um usuário pelo PIS.
     * Busca todos e filtra — adequado para poucos usuários.
     *
     * @param pis PIS do funcionário (com ou sem zeros à esquerda)
     * @return UsuarioDTO se encontrado, ou null
     */
    public UsuarioDTO buscarPorPis(String pis) {
        if (pis == null || pis.isBlank()) return null;

        // Normaliza o PIS para comparação
        String pisNorm = normalizarPis(pis);

        return listarTodos().stream()
                .filter(u -> pisNorm.equals(u.getPisFormatado()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Busca uma página de usuários.
     *
     * @param offset índice de início
     * @return lista de usuários da página, ou null se houver erro
     */
    private List<UsuarioDTO> buscarPagina(int offset) {
        try {
            String session = sessionManager.getSessionValida();
            if (session == null) {
                log.error("Sem sessão válida para buscar usuários.");
                return null;
            }
    
            String body = String.format(
                    "{\"limit\":%d,\"offset\":%d,\"include_templates\":false}",
                    PAGE_SIZE, offset
            );
    
            // CORREÇÃO: session na query string — load_users.fcgi não aceita Cookie
            String url = authService.buildUrlComSession("/load_users.fcgi", session);
            log.debug("Buscando usuários. URL: {} | offset: {}", url, offset);
    
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
                UsuarioResponseDTO resultado = objectMapper.readValue(
                        response.body(),
                        UsuarioResponseDTO.class
                );
                return resultado.getUsers() != null
                        ? resultado.getUsers()
                        : Collections.emptyList();
            }
    
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Sessão rejeitada (HTTP {}). Renovando...",
                        response.statusCode());
                sessionManager.renovarTokenForcado();
            }
    
            log.error("Erro ao buscar usuários. HTTP {}: {}",
                    response.statusCode(), response.body());
            return null;
    
        } catch (Exception e) {
            log.error("Erro de comunicação ao buscar usuários: {}", e.getMessage(), e);
            return null;
        }
    }

    /** Normaliza PIS removendo não-dígitos e preenchendo com zeros à esquerda */
    private String normalizarPis(String pis) {
        String soDigitos = pis.replaceAll("\\D", "");
        if (soDigitos.isEmpty()) return "000000000000";
        return String.format("%012d", Long.parseLong(soDigitos));
    }

    /**
 * Busca um usuário pelo nome (case-insensitive, aceita parte do nome).
 * Retorna o primeiro que casar com o termo buscado.
 *
 * Exemplos:
 *   "DONATA"   → encontra "DONATA APARECIDA MARTINS GARCIA"
 *   "garcia"   → encontra "DONATA APARECIDA MARTINS GARCIA"
 *   "patricia" → encontra "PATRICIA APARECIDA ALVES"
 *
 * @param nome termo de busca (parcial ou completo)
 * @return UsuarioDTO se encontrado, ou null
 */
public UsuarioDTO buscarPorNome(String nome) {
    if (nome == null || nome.isBlank()) return null;

    String termoLower = nome.toLowerCase().trim();

    return listarTodos().stream()
            .filter(u -> u.getName() != null
                    && u.getName().toLowerCase().contains(termoLower))
            .findFirst()
            .orElse(null);
}
}