package br.com.atom.api_chronus.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import br.com.atom.api_chronus.dto.AfdLineDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * Parseia o AFD retornado pelo iDClass.
 *
 * Layout oficial — Manual do REP iDClass / Portaria 671:
 *
 *   Campo 1: pos 001-009 (9)  → NSR (numérico)
 *   Campo 2: pos 010     (1)  → Tipo de registro = "3" (fixo para batidas)
 *   Campo 3: pos 011-018 (8)  → Data: ddmmaaaa
 *   Campo 4: pos 019-022 (4)  → Hora: hhmm
 *   Campo 6: pos 023-034 (12) → PIS do empregado
 *   Resto:   pos 035+         → Tipo de batida + CRC (extras, ignorados aqui)
 *
 * Total obrigatório: 34 chars.
 * Linhas de batida reais têm 38 chars (34 + 2 tipo batida + 2 CRC).
 */
@Slf4j
@Service
public class AfdParserService {

    /**
     * Regex baseado no layout oficial do manual.
     *
     * Grupos:
     *   1 → NSR  (9 dígitos)
     *   2 → DD   (2 dígitos)
     *   3 → MM   (2 dígitos, 01-12)
     *   4 → AAAA (4 dígitos)
     *   5 → HH   (2 dígitos, 00-23)
     *   6 → mm   (2 dígitos, 00-59)
     *   7 → PIS  (12 dígitos)
     *   8 → Tipo de batida (2 dígitos, opcional — vem após PIS)
     */
    private static final Pattern PATTERN_BATIDA = Pattern.compile(
            "^(\\d{9})"          // Campo 1: NSR (9)
            + "3"                 // Campo 2: tipo de registro = 3 (fixo)
            + "(\\d{2})"          // Campo 3: DD
            + "(0[1-9]|1[0-2])"   // Campo 3: MM válido 01-12
            + "(\\d{4})"          // Campo 3: AAAA
            + "([01]\\d|2[0-3])"  // Campo 4: HH válido 00-23
            + "([0-5]\\d)"        // Campo 4: mm válido 00-59
            + "(\\d{12})"         // Campo 6: PIS (12 dígitos)
            + "(\\d{2})?"         // Tipo de batida (opcional, 2 dígitos)
            + ".*$"               // CRC e resto — ignorado
    );

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("ddMMyyyyHHmm");

    /**
     * Parseia o conteúdo completo do AFD.
     *
     * @param conteudoAfd texto retornado pelo get_afd.fcgi
     * @return lista de batidas parseadas
     */
    public List<AfdLineDTO> parsear(String conteudoAfd) {
        List<AfdLineDTO> batidas = new ArrayList<>();

        if (conteudoAfd == null || conteudoAfd.isBlank()) {
            log.warn("Conteúdo AFD vazio.");
            return batidas;
        }

        String[] linhas = conteudoAfd.split("\\r?\\n");
        log.info("AFD: {} linhas. Iniciando parse...", linhas.length);

        int ignoradas = 0;

        for (String linha : linhas) {
            linha = linha.trim();

            if (linha.isBlank() || linha.contains(".txt")) {
                ignoradas++;
                continue;
            }

            AfdLineDTO batida = parsearLinha(linha);
            if (batida != null) {
                batidas.add(batida);
            } else {
                ignoradas++;
            }
        }

        log.info("Parse: {} batidas | {} ignoradas de {} linhas.",
                batidas.size(), ignoradas, linhas.length);
        return batidas;
    }

    /**
     * Parseia uma linha de batida usando o layout oficial do manual.
     * Linhas de outros tipos (4, 5, 6) não casam com o regex pois
     * não contêm o tipo de registro "3" na posição 10.
     */
    private AfdLineDTO parsearLinha(String linha) {
        Matcher m = PATTERN_BATIDA.matcher(linha);
        if (!m.matches()) {
            log.debug("Linha ignorada (não é batida tipo 3): '{}'", linha);
            return null;
        }

        try {
            long   nsr = Long.parseLong(m.group(1));
            String pis = m.group(7);

            // Tipo de batida vem após o PIS (campo opcional no manual)
            // 01=Entrada, 02=Saída, 03=Entrada intervalo, 04=Saída intervalo
            String tipoBatidaStr = m.group(8);
            int    tipoBatida    = (tipoBatidaStr != null)
                                   ? Integer.parseInt(tipoBatidaStr)
                                   : 0;

            // DDMMyyyyHHmm
            String dataHoraStr = m.group(2) + m.group(3) + m.group(4)
                               + m.group(5) + m.group(6);

            LocalDateTime dataHora = LocalDateTime.parse(dataHoraStr, FMT);

            return new AfdLineDTO(
                    nsr,
                    dataHora,
                    tipoBatida,
                    descricaoTipo(tipoBatida),
                    pis,
                    linha
            );

        } catch (Exception e) {
            log.debug("Erro ao parsear '{}': {}", linha, e.getMessage());
            return null;
        }
    }

    private String descricaoTipo(int tipo) {
        return switch (tipo) {
            case 1  -> "Entrada";
            case 2  -> "Saída";
            case 3  -> "Entrada intervalo";
            case 4  -> "Saída intervalo";
            default -> "Não classificado";
        };
    }
}