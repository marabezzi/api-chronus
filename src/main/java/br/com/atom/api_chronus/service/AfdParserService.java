package br.com.atom.api_chronus.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.atom.api_chronus.dto.AfdLineDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * Parseia o conteúdo do arquivo AFD retornado pelo iDClass.
 *
 * O AFD usa layout posicional fixo — Portaria 671/INMETRO.
 * ATENÇÃO: o iDClass grava o ano com formatos diferentes dependendo
 * da versão do firmware:
 *
 *   Firmware recente: DDMMAAAAHHmm (12 chars) — ano com 4 dígitos
 *   Firmware antigo:  DDMMAAHHmm   (10 chars) — ano com 2 dígitos
 *
 * O parser detecta automaticamente qual formato usar baseado
 * no valor do mês: se o mês for inválido (>12), significa que
 * o ano está com 4 dígitos e o layout é o recente.
 *
 * Layout recente (DDMMAAAAHHmm):
 *   Pos  0- 8  (9 chars)   → NSR
 *   Pos  9-20  (12 chars)  → Data+Hora: DDMMAAAAHHmm
 *   Pos 21-22  (2 chars)   → Tipo
 *   Pos 23-34  (12 chars)  → PIS/CPF
 *
 * Layout antigo (DDMMAAHHmm):
 *   Pos  0- 8  (9 chars)   → NSR
 *   Pos  9-18  (10 chars)  → Data+Hora: DDMMAAHHmm
 *   Pos 19-20  (2 chars)   → Tipo
 *   Pos 21-32  (12 chars)  → PIS/CPF
 */
@Slf4j
@Service
public class AfdParserService {

    /** Formato ano 4 dígitos: DDMMAAAAHHmm */
    private static final DateTimeFormatter FMT_ANO4 =
            DateTimeFormatter.ofPattern("ddMMyyyyHHmm");

    /** Formato ano 2 dígitos: DDMMAAHHmm */
    private static final DateTimeFormatter FMT_ANO2 =
            DateTimeFormatter.ofPattern("ddMMyyHHmm");

    /**
     * Parseia o conteúdo completo do AFD em lista de batidas.
     *
     * @param conteudoAfd string retornada pelo get_afd.fcgi
     * @return lista de batidas parseadas e válidas
     */
    public List<AfdLineDTO> parsear(String conteudoAfd) {
        List<AfdLineDTO> batidas = new ArrayList<>();

        if (conteudoAfd == null || conteudoAfd.isBlank()) {
            log.warn("Conteúdo AFD vazio ou nulo.");
            return batidas;
        }

        String[] linhas = conteudoAfd.split("\\r?\\n");
        log.info("AFD recebido: {} linhas. Iniciando parse...", linhas.length);

        int ignoradas = 0;

        for (String linha : linhas) {
            linha = linha.trim();

            // Ignora vazias e nome do arquivo na última linha
            if (linha.isBlank() || linha.contains(".txt")) {
                ignoradas++;
                continue;
            }

            // Linhas menores que 30 chars são especiais ou inválidas
            if (linha.length() < 30) {
                ignoradas++;
                log.debug("Linha ignorada (tamanho {}): {}", linha.length(), linha);
                continue;
            }

            AfdLineDTO batida = parsearLinha(linha);
            if (batida != null) {
                batidas.add(batida);
            } else {
                ignoradas++;
            }
        }

        log.info("Parse concluído. {} batidas válidas | {} ignoradas de {} linhas.",
                batidas.size(), ignoradas, linhas.length);
        return batidas;
    }

    /**
     * Parseia uma única linha do AFD detectando automaticamente
     * se o formato é de ano com 2 ou 4 dígitos.
     *
     * Estratégia de detecção:
     *   1. Tenta formato ano 4 dígitos (DDMMAAAAHHmm) — pos 9-20
     *   2. Se falhar, tenta formato ano 2 dígitos (DDMMAAHHmm) — pos 9-18
     *   3. Se ambos falharem, loga e retorna null
     *
     * @param linha linha individual do AFD
     * @return AfdLineDTO preenchido, ou null se inválida/especial
     */
    private AfdLineDTO parsearLinha(String linha) {
        try {
            // ── NSR: posições 0-8 (9 chars) ──────────────────────────
            long nsr = Long.parseLong(linha.substring(0, 9));

            // ── Tenta ano 4 dígitos primeiro ──────────────────────────
            // Formato: DDMMAAAAHHmm (12 chars), tipo em 21-22, PIS em 23-34
            LocalDateTime dataHora = null;
            int tipo;
            String pis;

            try {
                String dataHoraStr = linha.substring(9, 21);
                dataHora = LocalDateTime.parse(dataHoraStr, FMT_ANO4);
                tipo     = Integer.parseInt(linha.substring(21, 23));
                pis      = linha.length() >= 35 ? linha.substring(23, 35) : "";
            } catch (DateTimeParseException | NumberFormatException e) {
                // ── Falhou com ano 4 dígitos → tenta ano 2 dígitos ───
                // Formato: DDMMAAHHmm (10 chars), tipo em 19-20, PIS em 21-32
                try {
                    String dataHoraStr = linha.substring(9, 19);
                    dataHora = LocalDateTime.parse(dataHoraStr, FMT_ANO2);
                    tipo     = Integer.parseInt(linha.substring(19, 21));
                    pis      = linha.length() >= 33 ? linha.substring(21, 33) : "";
                } catch (DateTimeParseException | NumberFormatException e2) {
                    // Nenhum formato funcionou — linha inválida ou especial
                    log.debug("Linha não parseável (nenhum formato): '{}'", linha);
                    return null;
                }
            }

            // Tipo 6 = cabeçalho/rodapé do equipamento — ignora
            if (tipo == 6) {
                log.debug("Linha tipo 6 ignorada (cabeçalho/rodapé): {}", linha);
                return null;
            }

            return new AfdLineDTO(
                    nsr,
                    dataHora,
                    tipo,
                    descricaoTipo(tipo),
                    pis,
                    linha
            );

        } catch (Exception e) {
            log.debug("Erro inesperado ao parsear linha '{}': {}", linha, e.getMessage());
            return null;
        }
    }

    /**
     * Converte o código numérico do tipo de batida em descrição legível.
     * Baseado na Portaria 671/INMETRO.
     */
    private String descricaoTipo(int tipo) {
        return switch (tipo) {
            case 1  -> "Entrada";
            case 2  -> "Saída";
            case 3  -> "Entrada intervalo";
            case 4  -> "Saída intervalo";
            default -> "Não identificado (" + tipo + ")";
        };
    }
}