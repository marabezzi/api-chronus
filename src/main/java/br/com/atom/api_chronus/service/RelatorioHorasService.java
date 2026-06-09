package br.com.atom.api_chronus.service;


import br.com.atom.api_chronus.dto.*;
import br.com.atom.api_chronus.entity.BatidaPonto;
import br.com.atom.api_chronus.entity.UsuarioPonto;
import br.com.atom.api_chronus.repository.BatidaPontoRepository;
import br.com.atom.api_chronus.repository.UsuarioPontoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço para geração do Relatório de Horas por Funcionário.
 *
 * Reutiliza os dados do banco (Passo D) para calcular:
 *   - Total de horas por funcionário no período
 *   - Breakdown semanal
 *   - Breakdown diário (igual ao espelho)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioHorasService {

    private final BatidaPontoRepository  batidaRepo;
    private final UsuarioPontoRepository usuarioRepo;

    private static final DateTimeFormatter FMT_ENTRADA =
            DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter FMT_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    // ─────────────────────────────────────────────────────────────────────
    // TODOS OS FUNCIONÁRIOS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gera o relatório de horas de todos os funcionários em um período.
     *
     * @param dataInicial ddMMyyyy
     * @param dataFinal   ddMMyyyy
     */
    @SuppressWarnings("null")
    public RelatorioHorasResponseDTO gerarTodos(
            String dataInicial, String dataFinal) {

        LocalDate inicio = LocalDate.parse(dataInicial, FMT_ENTRADA);
        LocalDate fim    = LocalDate.parse(dataFinal,   FMT_ENTRADA);

        log.info("Gerando relatório de horas — todos | {} a {}", inicio, fim);

        // Busca todos os usuários cadastrados no banco
        List<UsuarioPonto> usuarios = usuarioRepo.findAll();

        List<RelatorioHorasFuncionarioDTO> funcionarios = new ArrayList<>();
        int totalMinGeral = 0;

        for (UsuarioPonto usuario : usuarios) {
            RelatorioHorasFuncionarioDTO rel =
                    gerarParaFuncionario(usuario.getPisFormatado(), inicio, fim);

            if (rel != null && rel.getTotalDias() > 0) {
                funcionarios.add(rel);

                // Acumula total geral
                String[] p = rel.getTotalHoras().split(":");
                totalMinGeral += Integer.parseInt(p[0]) * 60
                               + Integer.parseInt(p[1]);
            }
        }

        // Ordena por nome
        funcionarios.sort(
                Comparator.comparing(RelatorioHorasFuncionarioDTO::getNome));

        log.info("Relatório gerado: {} funcionários com ponto no período.",
                funcionarios.size());

        return new RelatorioHorasResponseDTO(
                inicio.format(FMT_ISO),
                fim.format(FMT_ISO),
                funcionarios.size(),
                formatarMinutos(totalMinGeral),
                funcionarios
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // UM FUNCIONÁRIO
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gera o relatório de horas de um único funcionário.
     *
     * @param pis         PIS com ou sem zeros à esquerda
     * @param dataInicial ddMMyyyy
     * @param dataFinal   ddMMyyyy
     */
    public RelatorioHorasFuncionarioDTO gerarPorPis(
            String pis, String dataInicial, String dataFinal) {

        LocalDate inicio = LocalDate.parse(dataInicial, FMT_ENTRADA);
        LocalDate fim    = LocalDate.parse(dataFinal,   FMT_ENTRADA);
        String pisNorm   = normalizarPis(pis);

        log.info("Gerando relatório de horas — PIS: {} | {} a {}",
                pisNorm, inicio, fim);

        return gerarParaFuncionario(pisNorm, inicio, fim);
    }

    // ─────────────────────────────────────────────────────────────────────
    // IMPLEMENTAÇÃO INTERNA
    // ─────────────────────────────────────────────────────────────────────
    @SuppressWarnings("null")
    private RelatorioHorasFuncionarioDTO gerarParaFuncionario(
            String pis, LocalDate inicio, LocalDate fim) {

        // Busca nome do funcionário
        String nome = usuarioRepo.findByPisFormatado(pis)
                .map(UsuarioPonto::getName)
                .orElse("Nao identificado");

        // Busca batidas no banco
        List<BatidaPonto> batidas = batidaRepo
                .findByPisAndDateTimeBetweenOrderByDateTimeAsc(
                        pis,
                        inicio.atStartOfDay(),
                        fim.atTime(23, 59, 59));

        if (batidas.isEmpty()) {
            return new RelatorioHorasFuncionarioDTO(
                    pis, nome, 0, "00:00", "00:00",
                    Collections.emptyList(), Collections.emptyList());
        }

        // Agrupa por dia
        Map<LocalDate, List<BatidaPonto>> porDia = batidas.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getDateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<EspelhoDiaDTO> dias        = new ArrayList<>();
        int                 totalMinutos = 0;

        for (Map.Entry<LocalDate, List<BatidaPonto>> entry : porDia.entrySet()) {
            LocalDate            data    = entry.getKey();
            List<BatidaPonto>    marcDia = entry.getValue();
            List<EspelhoMarcacaoDTO> marcacoes = new ArrayList<>();

            boolean impar         = marcDia.size() % 2 != 0;
            int     minutosNoDia  = 0;
            int     seqES         = 1;
            boolean entrada       = true;
            LocalDateTime ultimaEntrada = null;

            for (BatidaPonto batida : marcDia) {
                String tipo;
                if (entrada) {
                    tipo          = "Entrada";
                    ultimaEntrada = batida.getDateTime();
                } else {
                    tipo = "Saida";
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
                        tipo, seqES));

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

        // Breakdown semanal
        List<EspelhoSemanaDTO> semanas = calcularSemanas(dias);

        // Média diária
        int mediaMins = dias.isEmpty() ? 0 : totalMinutos / dias.size();

        return new RelatorioHorasFuncionarioDTO(
                pis,
                nome,
                dias.size(),
                formatarMinutos(totalMinutos),
                formatarMinutos(mediaMins),
                semanas,
                dias
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private List<EspelhoSemanaDTO> calcularSemanas(List<EspelhoDiaDTO> dias) {
        if (dias == null || dias.isEmpty()) return Collections.emptyList();

        Map<String, List<EspelhoDiaDTO>> porSemana = new LinkedHashMap<>();

        for (EspelhoDiaDTO dia : dias) {
            LocalDate data    = LocalDate.parse(dia.getData(), FMT_ISO);
            LocalDate segunda = data.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate domingo = segunda.plusDays(6);

            String chave = "Seg " + segunda.format(
                    DateTimeFormatter.ofPattern("dd/MM"))
                    + " - Dom " + domingo.format(
                    DateTimeFormatter.ofPattern("dd/MM"));

            porSemana.computeIfAbsent(chave, k -> new ArrayList<>()).add(dia);
        }

        List<EspelhoSemanaDTO> semanas = new ArrayList<>();

        for (Map.Entry<String, List<EspelhoDiaDTO>> entry
                : porSemana.entrySet()) {

            List<EspelhoDiaDTO> diasSem = entry.getValue();

            int totalMin = diasSem.stream()
                    .mapToInt(d -> {
                        String[] p = d.getTotalTrabalhado().split(":");
                        return Integer.parseInt(p[0]) * 60
                             + Integer.parseInt(p[1]);
                    }).sum();

            int diasTrab  = diasSem.size();
            int mediaMin  = diasTrab > 0 ? totalMin / diasTrab : 0;

            semanas.add(new EspelhoSemanaDTO(
                    entry.getKey(),
                    diasTrab,
                    formatarMinutos(totalMin),
                    formatarMinutos(mediaMin)
            ));
        }

        return semanas;
    }

    private String normalizarPis(String pis) {
        if (pis == null || pis.isBlank()) return "000000000000";
        String d = pis.replaceAll("\\D", "");
        return String.format("%012d", Long.parseLong(d));
    }

    private String formatarMinutos(int minutos) {
        return String.format("%02d:%02d", minutos / 60, minutos % 60);
    }

    private String nomeDiaSemana(LocalDate data) {
        return data.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.of("pt", "BR"));
    }
}