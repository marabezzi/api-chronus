package br.com.atom.api_chronus.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.atom.api_chronus.config.AfdtEmpresaConfig;
import br.com.atom.api_chronus.dto.AfdLineDTO;
import br.com.atom.api_chronus.dto.AfdResponseDTO;
import br.com.atom.api_chronus.dto.AfdtRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gera o AFDT — Arquivo-Fonte de Dados Tratado.
 *
 * Especificação: Portaria 1510/2009 MTE, Anexo I, seção 2.
 *
 * O AFDT é gerado pelo sistema (não pelo relógio) processando
 * as batidas do AFD e organizando-as por funcionário e jornada.
 *
 * Estrutura do arquivo gerado:
 *   - 1 registro tipo "1" (cabeçalho)
 *   - N registros tipo "2" (detalhe — uma linha por batida)
 *   - 1 registro tipo "9" (trailer)
 *
 * Regras aplicadas (conforme Portaria 1510):
 *   - Batidas agrupadas por PIS + data
 *   - Ordenadas cronologicamente
 *   - Alternância E/S: primeira = Entrada, segunda = Saída, etc.
 *   - Número sequencial E/S por jornada: E1/S1, E2/S2, etc.
 *   - Tipo de marcação = "O" (original eletrônico) para todas
 *   - Campo motivo vazio (sem desconsiderações ou inclusões manuais)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfdtGeneratorService {

    private final PunchLogService punchLogService;
    private final AfdtEmpresaConfig empresaConfig;

    private static final DateTimeFormatter FMT_DATA  =
            DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter FMT_HORA  =
            DateTimeFormatter.ofPattern("HHmm");

    /**
     * Gera o conteúdo completo do AFDT como string.
     *
     * @param request parâmetros de filtragem (período e PIS)
     * @return string com o AFDT no formato posicional da Portaria 1510
     */
        public String gerar(AfdtRequestDTO request) {
        log.info("Iniciando geração do AFDT...");

        // 1. Busca todas as batidas do AFD
        AfdResponseDTO afd = punchLogService.buscarBatidas(1L);
        if (afd == null || afd.getBatidas() == null || afd.getBatidas().isEmpty()) {
            log.error("Sem batidas disponíveis para gerar o AFDT.");
            return null;
        }

        List<AfdLineDTO> batidas = afd.getBatidas();

        // 2. Aplica filtro de período
        batidas = filtrarPorPeriodo(batidas, request);

        // 3. Aplica filtro de PIS
        if (request.getPis() != null && !request.getPis().isBlank()) {
            String pisFiltro = normalizarPis(request.getPis());
            batidas = batidas.stream()
                    .filter(b -> pisFiltro.equals(b.getPis()))
                    .collect(Collectors.toList());
        }

        if (batidas.isEmpty()) {
            log.warn("Nenhuma batida encontrada para os filtros informados.");
            return null;
        }

        // 4. Determina período real dos dados
        LocalDateTime dtInicial = batidas.stream()
                .map(AfdLineDTO::getDateTime)
                .min(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());

        LocalDateTime dtFinal = batidas.stream()
                .map(AfdLineDTO::getDateTime)
                .max(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());

        LocalDateTime dtGeracao = LocalDateTime.now();

        // 5. Monta o arquivo
        StringBuilder sb       = new StringBuilder();
        int           seq      = 1;          // sequencial de registro
        int           totalDet = 0;          // contador de registros tipo 2

        // ── Cabeçalho (tipo 1) ────────────────────────────────────────────
        sb.append(gerarCabecalho(seq++, dtInicial, dtFinal, dtGeracao));
        sb.append("\n");

        // ── Detalhes (tipo 2) — uma linha por batida ──────────────────────
        // Agrupa por PIS + data para calcular sequencial E/S
        Map<String, List<AfdLineDTO>> porPisEData = batidas.stream()
                .sorted(Comparator.comparing(AfdLineDTO::getDateTime))
                .collect(Collectors.groupingBy(
                        b -> b.getPis() + "_" + b.getDateTime().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

                for (Map.Entry<String, List<AfdLineDTO>> entry : porPisEData.entrySet()) {
                    List<AfdLineDTO> jornada = entry.getValue();
                
                    int     seqES   = 1;
                    boolean entrada = true;
                
                    for (int i = 0; i < jornada.size(); i++) {
                        AfdLineDTO batida = jornada.get(i);
                
                        boolean ultimaBatida   = (i == jornada.size() - 1);
                        boolean jornadaImpar   = (jornada.size() % 2 != 0);
                        boolean deveDescartar  = ultimaBatida && jornadaImpar && entrada;
                
                        // Se a jornada tem número ímpar de batidas, a última
                        // (que seria uma Entrada sem Saída correspondente) é desconsiderada
                        // conforme Portaria 1510, seção 2.2, item b.
                        String tipoMarcacao = deveDescartar ? "D" : (entrada ? "E" : "S");
                        String motivo       = deveDescartar
                                ? padDireita("MARCACAO SEM PAR - AGUARDANDO SAIDA", 100)
                                : padDireita("", 100);
                
                        sb.append(gerarDetalheComMotivo(seq++, batida, tipoMarcacao, seqES, motivo));
                        sb.append("\n");
                        totalDet++;
                
                        if (!entrada || deveDescartar) {
                            seqES++;
                        }
                        if (!deveDescartar) {
                            entrada = !entrada;
                        }
                    }
                }

        // ── Trailer (tipo 9) ──────────────────────────────────────────────
        sb.append(gerarTrailer(seq, totalDet));
        sb.append("\n");

        log.info("AFDT gerado: {} registros de detalhe.", totalDet);
        return sb.toString();
    }

    // ── Montadores de linha ───────────────────────────────────────────────

    /**
     * Cabeçalho — tipo 1 (215 chars).
     *
     * Layout Portaria 1510, seção 2.1:
     *   001-009 (9)  → sequencial
     *   010     (1)  → tipo "1"
     *   011     (1)  → tipo identificador "1"=CNPJ
     *   012-025 (14) → CNPJ
     *   026-037 (12) → CEI
     *   038-187 (150)→ razão social
     *   188-195 (8)  → data inicial ddmmaaaa
     *   196-203 (8)  → data final ddmmaaaa
     *   204-211 (8)  → data geração ddmmaaaa
     *   212-215 (4)  → hora geração hhmm
     */
    private String gerarCabecalho(int seq, LocalDateTime dtInicial,
                                   LocalDateTime dtFinal, LocalDateTime dtGeracao) {
        return String.format("%-9s", padZero(seq, 9))
                + "1"
                + "1"
                + padZero(empresaConfig.getCnpj(), 14)
                + padZero(empresaConfig.getCei(), 12)
                + padDireita(empresaConfig.getRazaoSocial(), 150)
                + dtInicial.format(FMT_DATA)
                + dtFinal.format(FMT_DATA)
                + dtGeracao.format(FMT_DATA)
                + dtGeracao.format(FMT_HORA);
    }

    /**
     * Detalhe — tipo 2 (155 chars).
     *
     * Layout Portaria 1510, seção 2.2:
     *   001-009 (9)   → sequencial
     *   010     (1)   → tipo "2"
     *   011-018 (8)   → data ddmmaaaa
     *   019-022 (4)   → hora hhmm
     *   023-034 (12)  → PIS
     *   035-051 (17)  → nº fabricação REP
     *   052     (1)   → tipo marcação E/S/D
     *   053-054 (2)   → sequencial E/S da jornada
     *   055     (1)   → tipo registro O/I/P
     *   056-155 (100) → motivo (vazio para registros originais)
     */
    /**
     * Detalhe tipo 2 — 155 chars.
     * Agora aceita motivo variável para registros desconsiderados (tipo D).
     */
    private String gerarDetalheComMotivo(int seq, AfdLineDTO batida,
        String tipoMarcacao, int seqES,
        String motivo) {
    return padZero(seq, 9)
    + "2"
    + batida.getDateTime().format(FMT_DATA)
    + batida.getDateTime().format(FMT_HORA)
    + padZero(batida.getPis(), 12)
    + padZero(empresaConfig.getNumFabricacao(), 17)
    + tipoMarcacao                      // E, S ou D
    + padZero(seqES, 2)                // 01, 02...
    + "O"                               // Original eletrônico
    + motivo;                           // 100 chars
    }
    /**
     * Trailer — tipo 9.
     *
     * Layout Portaria 1510, seção 2.3:
     *   001-009 (9) → sequencial
     *   010     (1) → tipo "9"
     */
    private String gerarTrailer(int seq, int totalDetalhes) {
        // Inclui totalizador informativo após o tipo 9
        return padZero(seq, 9) + "9";
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Filtra batidas pelo período informado no request.
     * Se não informado, retorna todas.
     */
    private List<AfdLineDTO> filtrarPorPeriodo(List<AfdLineDTO> batidas,
                                                AfdtRequestDTO request) {
        LocalDate inicio = null;
        LocalDate fim    = null;

        try {
            if (request.getDataInicial() != null && !request.getDataInicial().isBlank()) {
                inicio = LocalDate.parse(request.getDataInicial(),
                        DateTimeFormatter.ofPattern("ddMMyyyy"));
            }
            if (request.getDataFinal() != null && !request.getDataFinal().isBlank()) {
                fim = LocalDate.parse(request.getDataFinal(),
                        DateTimeFormatter.ofPattern("ddMMyyyy"));
            }
        } catch (Exception e) {
            log.warn("Formato de data inválido — retornando sem filtro de período.");
            return batidas;
        }

        final LocalDate fInicio = inicio;
        final LocalDate fFim    = fim;

        return batidas.stream()
                .filter(b -> {
                    LocalDate data = b.getDateTime().toLocalDate();
                    if (fInicio != null && data.isBefore(fInicio)) return false;
                    if (fFim    != null && data.isAfter(fFim))     return false;
                    return true;
                })
                .collect(Collectors.toList());
    }

    /** Normaliza PIS removendo não-dígitos e preenchendo com zeros à esquerda */
    private String normalizarPis(String pis) {
        String soDigitos = pis.replaceAll("\\D", "");
        return String.format("%012d", Long.parseLong(soDigitos));
    }

    /** Preenche com zeros à esquerda até o tamanho desejado */
    private String padZero(long valor, int tamanho) {
        return String.format("%0" + tamanho + "d", valor);
    }

    /** Preenche string com zeros à esquerda */
    private String padZero(String valor, int tamanho) {
        if (valor == null) valor = "";
        valor = valor.replaceAll("\\D", ""); // mantém só dígitos
        return String.format("%0" + tamanho + "d",
                valor.isEmpty() ? 0 : Long.parseLong(valor.substring(0,
                        Math.min(valor.length(), 18))));
    }

    /** Preenche com espaços à direita até o tamanho desejado */
    private String padDireita(String valor, int tamanho) {
        if (valor == null) valor = "";
        if (valor.length() > tamanho) valor = valor.substring(0, tamanho);
        return String.format("%-" + tamanho + "s", valor);
    }
}
