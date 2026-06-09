package br.com.atom.api_chronus.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.atom.api_chronus.dto.EspelhoDiaDTO;
import br.com.atom.api_chronus.dto.EspelhoMarcacaoDTO;
import br.com.atom.api_chronus.dto.EspelhoRequestDTO;
import br.com.atom.api_chronus.dto.EspelhoResponseDTO;
import br.com.atom.api_chronus.dto.EspelhoSemanaDTO;
import br.com.atom.api_chronus.entity.BatidaPonto;
import br.com.atom.api_chronus.entity.UsuarioPonto;
import br.com.atom.api_chronus.repository.BatidaPontoRepository;
import br.com.atom.api_chronus.repository.UsuarioPontoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço para geração do Espelho de Ponto.
 *
 * PASSO D: consulta o banco PostgreSQL em vez do relógio.
 * Muito mais rápido — evita chamada HTTPS + parse do AFD a cada request.
 *
 * Fluxo:
 *   1. Busca batidas no banco filtradas por PIS + período
 *   2. Busca o nome do funcionário no banco de usuários
 *   3. Agrupa por dia e calcula totais
 *   4. Retorna o EspelhoResponseDTO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspelhoService {

    private final BatidaPontoRepository  batidaRepo;
    private final UsuarioPontoRepository usuarioRepo;

    private static final DateTimeFormatter FMT_ENTRADA =
            DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter FMT_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Gera o Espelho de Ponto consultando o banco de dados.
     *
     * @param request PIS + dataInicial + dataFinal
     * @return EspelhoResponseDTO completo
     */
    @SuppressWarnings("null")
    public EspelhoResponseDTO gerar(EspelhoRequestDTO request) {

        // ── 1. Valida e normaliza o PIS ───────────────────────────────────
        String pis = normalizarPis(request.getPis());
        if (pis == null) {
            log.error("PIS inválido: {}", request.getPis());
            return null;
        }

        // ── 2. Valida o período ───────────────────────────────────────────
        LocalDate inicio, fim;
        try {
            inicio = LocalDate.parse(request.getDataInicial(), FMT_ENTRADA);
            fim    = LocalDate.parse(request.getDataFinal(),   FMT_ENTRADA);
        } catch (Exception e) {
            log.error("Datas inválidas: {} / {}",
                    request.getDataInicial(), request.getDataFinal());
            return null;
        }

        log.info("Gerando espelho do banco. PIS: {} | {} a {}", pis, inicio, fim);

        // ── 3. Busca o nome do funcionário no banco ───────────────────────
        String nome = usuarioRepo.findByPisFormatado(pis)
                .map(UsuarioPonto::getName)
                .orElse("Não identificado");

        // ── 4. Busca batidas no banco filtradas por PIS + período ─────────
        LocalDateTime iniciodt = inicio.atStartOfDay();
        LocalDateTime fimdt    = fim.atTime(23, 59, 59);

        List<BatidaPonto> batidas = batidaRepo
                .findByPisAndDateTimeBetweenOrderByDateTimeAsc(pis, iniciodt, fimdt);

        log.info("Batidas encontradas no banco: {}", batidas.size());

        if (batidas.isEmpty()) {
            log.warn("Sem batidas para PIS {} no período. Banco sincronizado?", pis);
        }

        // ── 5. Agrupa por dia ─────────────────────────────────────────────
        Map<LocalDate, List<BatidaPonto>> porDia = batidas.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getDateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        // ── 6. Monta os dias do espelho ───────────────────────────────────
        List<EspelhoDiaDTO> dias = new ArrayList<>();
        int totalMinutos = 0;

        for (Map.Entry<LocalDate, List<BatidaPonto>> entry : porDia.entrySet()) {
            LocalDate data    = entry.getKey();
            List<BatidaPonto> marcDia = entry.getValue();
            List<EspelhoMarcacaoDTO> marcacoes = new ArrayList<>();

            boolean impar        = marcDia.size() % 2 != 0;
            int     minutosNoDia = 0;
            int     seqES        = 1;
            boolean entrada      = true;
            LocalDateTime ultimaEntrada = null;

            for (BatidaPonto batida : marcDia) {
                String tipo;
                if (entrada) {
                    tipo         = "Entrada";
                    ultimaEntrada = batida.getDateTime();
                } else {
                    tipo = "Saída";
                    if (ultimaEntrada != null) {
                        long mins = java.time.Duration
                                .between(ultimaEntrada, batida.getDateTime())
                                .toMinutes();
                        if (mins > 0) minutosNoDia += mins;
                        ultimaEntrada = null;
                    }
                }

                marcacoes.add(new EspelhoMarcacaoDTO(
                        batida.getDateTime().format(FMT_HORA),
                        tipo,
                        seqES
                ));

                if (!entrada) seqES++;
                entrada = !entrada;
            }

            totalMinutos += minutosNoDia;

            dias.add(new EspelhoDiaDTO(
                    data.format(FMT_ISO),
                    nomeDiaSemana(data),
                    marcacoes,
                    marcacoes.size(),
                    formatarMinutos(minutosNoDia),
                    impar
            ));
        }

        // ── 7. Retorna o espelho ──────────────────────────────────────────
        return new EspelhoResponseDTO(
         pis, nome,
         inicio.format(FMT_ISO),
         fim.format(FMT_ISO),
         dias,
         dias.size(),
         formatarMinutos(totalMinutos),
         calcularSemanas(dias)   // ← novo
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String normalizarPis(String pis) {
        if (pis == null || pis.isBlank()) return null;
        try {
            String d = pis.replaceAll("\\D", "");
            return String.format("%012d", Long.parseLong(d));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatarMinutos(int minutos) {
        return String.format("%02d:%02d", minutos / 60, minutos % 60);
    }

    private String nomeDiaSemana(LocalDate data) {
        return data.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.of("pt", "BR"));
    }


    /**
 * Agrupa os dias por semana ISO (segunda a domingo) e calcula
 * total de horas, dias trabalhados e média diária por semana.
 *
 * A semana é identificada pelo primeiro e último dia com ponto
 * dentro da semana ISO: "Seg DD/MM - Dom DD/MM".
 */
private List<EspelhoSemanaDTO> calcularSemanas(List<EspelhoDiaDTO> dias) {
    if (dias == null || dias.isEmpty()) return Collections.emptyList();

    // Agrupa dias pelo número da semana ISO do ano
    // Ex: semana ISO 19 de 2026 = todos os dias dessa semana
    Map<String, List<EspelhoDiaDTO>> porSemana = new LinkedHashMap<>();

    for (EspelhoDiaDTO dia : dias) {
        LocalDate data = LocalDate.parse(dia.getData(), FMT_ISO);

        // Calcula segunda e domingo da semana ISO deste dia
        LocalDate segunda = data.with(
                java.time.temporal.TemporalAdjusters
                        .previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate domingo = segunda.plusDays(6);

        // Chave: "Seg DD/MM - Dom DD/MM"
        String chave = "Seg " + segunda.format(DateTimeFormatter.ofPattern("dd/MM"))
                + " - Dom " + domingo.format(DateTimeFormatter.ofPattern("dd/MM"));

        porSemana.computeIfAbsent(chave, k -> new ArrayList<>()).add(dia);
    }

    // Monta o DTO de cada semana
    List<EspelhoSemanaDTO> semanas = new ArrayList<>();

    for (Map.Entry<String, List<EspelhoDiaDTO>> entry : porSemana.entrySet()) {
        String              chave     = entry.getKey();
        List<EspelhoDiaDTO> diasSem   = entry.getValue();

        // Soma os minutos trabalhados na semana
        int totalMin = diasSem.stream()
                .mapToInt(d -> {
                    String[] p = d.getTotalTrabalhado().split(":");
                    return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
                })
                .sum();

        int diasTrabalhados = diasSem.size();
        int mediaMin        = diasTrabalhados > 0 ? totalMin / diasTrabalhados : 0;

        semanas.add(new EspelhoSemanaDTO(
                chave,
                diasTrabalhados,
                formatarMinutos(totalMin),
                formatarMinutos(mediaMin)
        ));
    }

    return semanas;
}
}