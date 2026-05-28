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
import br.com.atom.api_chronus.dto.PunchLogDTO;
import br.com.atom.api_chronus.dto.PunchLogResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço para buscar registros de batidas de ponto do iDClass.
 *
 * Endpoint correto do iDClass (REP):  POST /get_mftr_log.fcgi
 * Autenticação:                       Cookie: session={token}
 *
 * IMPORTANTE — diferença entre produtos ControlID:
 *   iDAccess (Controle de Acesso) → /load_objects.fcgi com object=access_logs
 *   iDClass  (Relógio de Ponto)   → /get_mftr_log.fcgi   ← este serviço
 *
 * Paginação via NSR (Número Sequencial de Registro):
 *   - Primeira chamada: startNsr = 1
 *   - Próximas chamadas: startNsr = nextNsr retornado pela resposta anterior
 *   - Fim: nextNsr = 0 ou ausente
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PunchLogService {

    private final IdClassConfig config;
    private final HttpClient httpClient;
    private final SessionManager sessionManager;
    private final IdClassAuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Registros por página — recomendado pela documentação iDClass */
    private static final int REGISTROS_POR_PAGINA = 50;

    /**
     * Busca uma página de batidas de ponto a partir de um NSR inicial.
     *
     * @param startNsr NSR de início da página (1 = começo, null = começo)
     * @return página de batidas, ou null se houver erro de comunicação
     */
    public PunchLogResponseDTO buscarPagina(Long startNsr) {
        try {
            // Obtém sessão válida — renova automaticamente se expirada
            String session = sessionManager.getSessionValida();
            if (session == null) {
                log.error("Sem sessão válida para buscar batidas de ponto.");
                return null;
            }

            // NSR padrão: começa do primeiro registro
            long nsr = (startNsr != null && startNsr > 0) ? startNsr : 1;

            // Monta o corpo: informa o NSR inicial e quantidade por página
            // { "start_nsr": 1, "qty": 50 }
            String bodyJson = String.format(
                    "{\"start_nsr\":%d,\"qty\":%d}",
                    nsr, REGISTROS_POR_PAGINA
            );

            String url = config.getBaseUrl() + "/get_mftr_log.fcgi";
            log.debug("Buscando batidas. URL: {} | start_nsr: {}", url, nsr);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    // Cookie de sessão obrigatório em todos os requests pós-login
                    .header("Cookie", authService.buildCookie(session))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                PunchLogResponseDTO resultado = objectMapper.readValue(
                        response.body(),
                        PunchLogResponseDTO.class
                );
                int total = resultado.getPunchLogs() != null
                        ? resultado.getPunchLogs().size() : 0;
                log.info("Batidas recebidas: {} | Próximo NSR: {}",
                        total, resultado.getNextNsr());
                return resultado;
            }

            // Sessão expirou no meio da operação — força renovação
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Sessão rejeitada (HTTP {}). Renovando token...",
                        response.statusCode());
                sessionManager.renovarTokenForcado();
            }

            log.error("Erro ao buscar batidas. HTTP {}: {}",
                    response.statusCode(), response.body());
            return null;

        } catch (Exception e) {
            log.error("Erro de comunicação com o relógio: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Busca TODAS as batidas percorrendo todas as páginas automaticamente.
     *
     * ATENÇÃO: o iDClass armazena até 25.000 registros.
     * Para rotinas diárias, prefira buscarPagina() com controle de NSR.
     *
     * @return lista completa de batidas, ou lista vazia se houver erro
     */
    public List<PunchLogDTO> buscarTodas() {
        List<PunchLogDTO> todas = new ArrayList<>();
        Long startNsr = 1L;
        int pagina = 1;

        log.info("Iniciando busca completa de batidas de ponto...");

        do {
            PunchLogResponseDTO resultado = buscarPagina(startNsr);

            if (resultado == null) {
                log.warn("Falha na página {}. Retornando {} registros coletados.",
                        pagina, todas.size());
                break;
            }

            if (resultado.getPunchLogs() != null) {
                todas.addAll(resultado.getPunchLogs());
            }

            log.debug("Página {} processada. Total acumulado: {}", pagina, todas.size());

            // Avança para o próximo NSR — 0 significa fim dos registros
            startNsr = resultado.getNextNsr();
            pagina++;

        } while (startNsr != null && startNsr > 0);

        log.info("Busca completa. Total de batidas: {}", todas.size());
        return todas;
    }

    /**
     * Busca batidas de um usuário específico pelo PIS/CPF.
     *
     * O iDClass não filtra por usuário no get_mftr_log diretamente.
     * Buscamos todas e filtramos em memória.
     * Para grandes volumes, considere filtrar por data primeiro.
     *
     * @param pis PIS ou CPF do funcionário
     * @return lista de batidas do usuário, ou lista vazia se não encontrar
     */
    public List<PunchLogDTO> buscarPorPis(String pis) {
        if (pis == null || pis.isBlank()) {
            return Collections.emptyList();
        }

        return buscarTodas().stream()
                // Compatível com firmware pré-671 (user_pis) e 671 (user_cpf)
                .filter(log -> pis.equals(log.getUserPis()) || pis.equals(log.getUserCpf()))
                .toList();
    }

}
