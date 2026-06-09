package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.entity.BatidaPonto;
import br.com.atom.api_chronus.entity.UsuarioPonto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Serviço de notificações via WhatsApp usando Evolution API.
 *
 * Dois tipos de mensagem:
 *   1. Comprovante por batida (CADA_BATIDA)
 *   2. Resumo diário (RESUMO_DIA) — enviado no horário configurado
 *
 * Setup da Evolution API:
 *   1. Acesse http://localhost:8082
 *   2. Crie a instância "chronus"
 *   3. Escaneie o QR Code com o WhatsApp do responsável
 *   4. Configure /api/config com a API Key e URL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private final ConfiguracaoService configService;
    private final HttpClient          httpClient =
            HttpClient.newHttpClient();
    private final ObjectMapper        mapper = new ObjectMapper();

    private static final DateTimeFormatter FMT_HORA =
            DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ─────────────────────────────────────────────────────────────────────
    // COMPROVANTE DE BATIDA
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Envia comprovante de batida individual.
     * Mesmo conteúdo do e-mail, formatado para WhatsApp.
     *
     * @param batida  batida recém sincronizada
     * @param usuario funcionário com WhatsApp habilitado
     */
    public void enviarComprovanteBatida(BatidaPonto batida,
                                         UsuarioPonto usuario) {
        if (!validarEnvio(usuario)) return;
        if (!"CADA_BATIDA".equals(usuario.getWhatsappPreferencia()))
            return;

        String mensagem = montarMensagemBatida(batida, usuario);
        enviar(usuario.getWhatsappNumero(), mensagem);
    }

    // ─────────────────────────────────────────────────────────────────────
    // RESUMO DIÁRIO
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Envia resumo diário das batidas.
     * Chamado pelo SyncService no horário configurado.
     *
     * @param usuario  funcionário
     * @param batidas  todas as batidas do dia
     */
    public void enviarResumoDiario(UsuarioPonto usuario,
                                    List<BatidaPonto> batidas) {
        if (!validarEnvio(usuario)) return;
        if (!"RESUMO_DIA".equals(usuario.getWhatsappPreferencia()))
            return;
        if (batidas == null || batidas.isEmpty()) return;

        String mensagem = montarMensagemResumoDia(usuario, batidas);
        enviar(usuario.getWhatsappNumero(), mensagem);
    }

    // ─────────────────────────────────────────────────────────────────────
    // MONTAGEM DAS MENSAGENS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Mensagem de comprovante individual — mesmo conteúdo do e-mail.
     *
     * ✅ *Comprovante de Ponto*
     *
     * 🕐 08:27
     * 📅 09/06/2026
     * 👤 DONATA G.
     * 🪪 PIS: 012952592162
     * 🔢 NSR: 000019590
     *
     * _JOSE NATAL CLERICE -ME_
     * _Chronus REP | Portaria 1510/MTE_
     */
    private String montarMensagemBatida(BatidaPonto batida,
                                         UsuarioPonto usuario) {
        return """
                ✅ *Comprovante de Ponto*

                🕐 *%s*
                📅 %s
                👤 %s
                🪪 PIS: %s
                🔢 NSR: %s

                _%s_
                _Chronus REP | Portaria 1510/MTE_
                """.formatted(
                batida.getDateTime().format(FMT_HORA),
                batida.getDateTime().format(FMT_DATA),
                usuario.getName(),
                usuario.getPisFormatado(),
                String.format("%09d", batida.getNsr()),
                configService.getEmpresaNome()
        );
    }

    /**
     * Mensagem de resumo diário.
     *
     * 📊 *Resumo do Dia — 09/06/2026*
     *
     * 👤 DONATA APARECIDA MARTINS GARCIA
     * 🪪 PIS: 012952592162
     *
     * ⏰ *Marcações:*
     * • 08:27 • 12:01 • 13:00 • 17:29
     *
     * ✅ Total trabalhado: 08:01
     *
     * _JOSE NATAL CLERICE -ME_
     */
    private String montarMensagemResumoDia(UsuarioPonto usuario,
                                            List<BatidaPonto> batidas) {
        String data = batidas.get(0).getDateTime().format(FMT_DATA);

        // Monta linha de marcações
        StringBuilder marcacoes = new StringBuilder();
        for (BatidaPonto b : batidas) {
            marcacoes.append("• ").append(
                    b.getDateTime().format(FMT_HORA)).append(" ");
        }

        // Calcula total trabalhado (pares E/S)
        String totalTrabalhado = calcularTotalTrabalhado(batidas);

        return """
                📊 *Resumo do Dia — %s*

                👤 *%s*
                🪪 PIS: %s

                ⏰ *Marcacoes:*
                %s

                %s Total trabalhado: *%s*

                _%s_
                """.formatted(
                data,
                usuario.getName(),
                usuario.getPisFormatado(),
                marcacoes.toString().trim(),
                batidas.size() % 2 == 0 ? "✅" : "⚠️",
                totalTrabalhado,
                configService.getEmpresaNome()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // ENVIO VIA EVOLUTION API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Envia mensagem de texto via Evolution API.
     *
     * POST {url}/message/sendText/{instancia}
     * Headers: apikey: {key}
     * Body: { "number": "5514999999999", "text": "..." }
     */
    private void enviar(String numero, String mensagem) {
        try {
            String url = configService.getWhatsappUrl()
                    + "/message/sendText/"
                    + configService.getWhatsappInstancia();

            String body = mapper.writeValueAsString(Map.of(
                    "number", numero,
                    "text",   mensagem
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("apikey",
                            configService.getWhatsappApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201
                    || response.statusCode() == 200) {
                log.info("WhatsApp enviado → {} | {}",
                        numero, mensagem.lines()
                                .findFirst().orElse(""));
            } else {
                log.warn("Falha ao enviar WhatsApp → {} | HTTP {}: {}",
                        numero, response.statusCode(),
                        response.body());
            }

        } catch (Exception e) {
            log.error("Erro ao enviar WhatsApp → {}: {}",
                    numero, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private boolean validarEnvio(UsuarioPonto usuario) {
        if (!configService.isWhatsappHabilitado()) {
            log.debug("WhatsApp desabilitado nas configuracoes.");
            return false;
        }
        if (!Boolean.TRUE.equals(usuario.getWhatsappHabilitado())) {
            return false;
        }
        if (usuario.getWhatsappNumero() == null
                || usuario.getWhatsappNumero().isBlank()) {
            log.debug("Funcionario {} sem numero WhatsApp.",
                    usuario.getName());
            return false;
        }
        return true;
    }

    /**
     * Calcula total trabalhado somando pares E/S.
     * Retorna "HH:mm".
     */
    private String calcularTotalTrabalhado(List<BatidaPonto> batidas) {
        int totalMin = 0;
        for (int i = 0; i + 1 < batidas.size(); i += 2) {
            long mins = java.time.Duration.between(
                    batidas.get(i).getDateTime(),
                    batidas.get(i + 1).getDateTime()
            ).toMinutes();
            if (mins > 0) totalMin += mins;
        }
        return String.format("%02d:%02d",
                totalMin / 60, totalMin % 60);
    }
}