package br.com.atom.api_chronus.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.atom.api_chronus.dto.AfdLineDTO;
import br.com.atom.api_chronus.dto.AfdResponseDTO;
import br.com.atom.api_chronus.dto.EspelhoDiaDTO;
import br.com.atom.api_chronus.dto.EspelhoMarcacaoDTO;
import br.com.atom.api_chronus.dto.EspelhoRequestDTO;
import br.com.atom.api_chronus.dto.EspelhoResponseDTO;
import br.com.atom.api_chronus.dto.UsuarioDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço para geração do Espelho de Ponto.
 *
 * Fluxo:
 *   1. Busca todas as batidas do AFD via PunchLogService
 *   2. Filtra pelo PIS e período informados
 *   3. Busca o nome do funcionário via UsuarioService
 *   4. Agrupa as batidas por dia
 *   5. Para cada dia, alterna E/S e calcula horas trabalhadas
 *   6. Monta e retorna o EspelhoResponseDTO
 *
 * Regras de negócio:
 *   - Marcações ordenadas cronologicamente dentro do dia
 *   - Alternância E/S: primeira = Entrada, segunda = Saída, etc.
 *   - Par completo = 1 Entrada + 1 Saída → calcula diferença em minutos
 *   - Dia com número ímpar de marcações → temInconsistencia = true
 *   - Total trabalhado = soma de todos os pares completos do dia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspelhoService {

    private final PunchLogService  punchLogService;
    private final UsuarioService   usuarioService;

    private static final DateTimeFormatter FMT_ENTRADA =
            DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter FMT_ISO_DATA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Gera o Espelho de Ponto de um funcionário para um período.
     *
     * @param request PIS + dataInicial + dataFinal
     * @return EspelhoResponseDTO completo, ou null se houver erro
     */
    public EspelhoResponseDTO gerar(EspelhoRequestDTO request) {
        // ── 1. Valida e normaliza o PIS ───────────────────────────────────
        String pis = normalizarPis(request.getPis());
        if (pis == null) {
            log.error("PIS inválido: {}", request.getPis());
            return null;
        }

        // ── 2. Valida e converte o período ────────────────────────────────
        LocalDate inicio, fim;
        try {
            inicio = LocalDate.parse(request.getDataInicial(), FMT_ENTRADA);
            fim    = LocalDate.parse(request.getDataFinal(),   FMT_ENTRADA);
        } catch (Exception e) {
            log.error("Datas inválidas: {} / {}",
                    request.getDataInicial(), request.getDataFinal());
            return null;
        }

        if (inicio.isAfter(fim)) {
            log.error("Data inicial {} é posterior à data final {}", inicio, fim);
            return null;
        }

        log.info("Gerando espelho de ponto. PIS: {} | Período: {} a {}", pis, inicio, fim);

        // ── 3. Busca o nome do funcionário ────────────────────────────────
        String nome = buscarNome(pis);

        // ── 4. Busca todas as batidas e filtra por PIS + período ──────────
        AfdResponseDTO afd = punchLogService.buscarBatidas(1L);
        if (afd == null || afd.getBatidas() == null) {
            log.error("Não foi possível buscar as batidas do relógio.");
            return null;
        }

        final LocalDate fInicio = inicio;
        final LocalDate fFim    = fim;

        List<AfdLineDTO> batidas = afd.getBatidas().stream()
                .filter(b -> pis.equals(b.getPis()))
                .filter(b -> {
                    LocalDate data = b.getDateTime().toLocalDate();
                    return !data.isBefore(fInicio) && !data.isAfter(fFim);
                })
                .sorted(Comparator.comparing(AfdLineDTO::getDateTime))
                .collect(Collectors.toList());

        log.info("Batidas encontradas para o período: {}", batidas.size());

        // ── 5. Agrupa por dia ─────────────────────────────────────────────
        Map<LocalDate, List<AfdLineDTO>> porDia = batidas.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getDateTime().toLocalDate(),
                        TreeMap::new,  // ordenado por data
                        Collectors.toList()
                ));

        // ── 6. Monta os dias do espelho ───────────────────────────────────
        List<EspelhoDiaDTO> dias = new ArrayList<>();
        int totalMinutos = 0;

        for (Map.Entry<LocalDate, List<AfdLineDTO>> entry : porDia.entrySet()) {
            LocalDate            data    = entry.getKey();
            List<AfdLineDTO>     marcDia = entry.getValue();
            List<EspelhoMarcacaoDTO> marcacoes = new ArrayList<>();

            boolean ímpar        = marcDia.size() % 2 != 0;
            int     minutosNoDia = 0;
            int     seqES        = 1;
            boolean entrada      = true;
            LocalDateTime ultimaEntrada = null;

            for (AfdLineDTO batida : marcDia) {
                String tipo;

                if (entrada) {
                    tipo         = "Entrada";
                    ultimaEntrada = batida.getDateTime();
                } else {
                    tipo = "Saída";
                    // Calcula minutos trabalhados no par
                    if (ultimaEntrada != null) {
                        long minutos = java.time.Duration
                                .between(ultimaEntrada, batida.getDateTime())
                                .toMinutes();
                        if (minutos > 0) {
                            minutosNoDia += minutos;
                        }
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
                    data.format(FMT_ISO_DATA),
                    nomeDiaSemana(data),
                    marcacoes,
                    marcacoes.size(),
                    formatarMinutos(minutosNoDia),
                    ímpar
            ));
        }

        // ── 7. Monta a resposta ───────────────────────────────────────────
        return new EspelhoResponseDTO(
                pis,
                nome,
                inicio.format(FMT_ISO_DATA),
                fim.format(FMT_ISO_DATA),
                dias,
                dias.size(),
                formatarMinutos(totalMinutos)
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Busca o nome do funcionário pelo PIS via UsuarioService.
     * Retorna "Não identificado" se não encontrar.
     */
    private String buscarNome(String pis) {
        try {
            UsuarioDTO usuario = usuarioService.buscarPorPis(pis);
            return usuario != null ? usuario.getName() : "Não identificado";
        } catch (Exception e) {
            log.warn("Não foi possível buscar o nome para o PIS {}: {}", pis, e.getMessage());
            return "Não identificado";
        }
    }

    /**
     * Normaliza o PIS para 12 dígitos com zeros à esquerda.
     * Retorna null se o PIS for inválido.
     */
    private String normalizarPis(String pis) {
        if (pis == null || pis.isBlank()) return null;
        try {
            String soDigitos = pis.replaceAll("\\D", "");
            return String.format("%012d", Long.parseLong(soDigitos));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Formata minutos totais para o formato HH:mm.
     * Exemplo: 510 minutos → "08:30"
     */
    private String formatarMinutos(int minutos) {
        int horas = minutos / 60;
        int mins  = minutos % 60;
        return String.format("%02d:%02d", horas, mins);
    }

    /**
     * Retorna o nome do dia da semana em português.
     * Exemplo: MONDAY → "Segunda-feira"
     */
    private String nomeDiaSemana(LocalDate data) {
        return data.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, Locale.of("pt", "BR"));
    }
}