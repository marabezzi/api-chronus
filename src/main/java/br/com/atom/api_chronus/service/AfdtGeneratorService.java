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
import br.com.atom.api_chronus.dto.AfdtRequestDTO;
import br.com.atom.api_chronus.entity.AfdtGerado;
import br.com.atom.api_chronus.entity.BatidaPonto;
import br.com.atom.api_chronus.repository.AfdtGeradoRepository;
import br.com.atom.api_chronus.repository.BatidaPontoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gera o AFDT — Arquivo-Fonte de Dados Tratado.
 *
 * PASSO D: consulta o banco PostgreSQL em vez do relógio.
 * O AFDT gerado também é salvo no banco (tabela afdt_gerados)
 * para histórico e reenvio sem necessidade de regenerar.
 *
 * Especificação: Portaria 1510/2009 MTE, Anexo I, seção 2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfdtGeneratorService {

    private final BatidaPontoRepository   batidaRepo;
    private final AfdtGeradoRepository   afdtRepo;
    private final AfdtEmpresaConfig      empresaConfig;

    private static final DateTimeFormatter FMT_DATA  =
            DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter FMT_HORA  =
            DateTimeFormatter.ofPattern("HHmm");

    /**
     * Gera o AFDT a partir do banco de dados e salva o resultado.
     *
     * @param request parâmetros de filtragem
     * @return conteúdo do AFDT como string
     */
    public String gerar(AfdtRequestDTO request) {
        log.info("Gerando AFDT do banco...");

        // ── 1. Busca batidas no banco ─────────────────────────────────────
        List<BatidaPonto> batidas = buscarBatidasFiltradas(request);

        if (batidas.isEmpty()) {
            log.warn("Nenhuma batida no banco para os filtros. Execute /api/sync primeiro.");
            return null;
        }

        // ── 2. Determina o período real ───────────────────────────────────
        LocalDateTime dtInicial = batidas.stream()
                .map(BatidaPonto::getDateTime)
                .min(Comparator.naturalOrder()).orElse(LocalDateTime.now());

        LocalDateTime dtFinal = batidas.stream()
                .map(BatidaPonto::getDateTime)
                .max(Comparator.naturalOrder()).orElse(LocalDateTime.now());

        LocalDateTime dtGeracao = LocalDateTime.now();

        // ── 3. Monta o arquivo ────────────────────────────────────────────
        StringBuilder sb  = new StringBuilder();
        int seq      = 1;
        int totalDet = 0;

        sb.append(gerarCabecalho(seq++, dtInicial, dtFinal, dtGeracao)).append("\n");

        // Agrupa por PIS + data para sequencial E/S
        Map<String, List<BatidaPonto>> porPisEData = batidas.stream()
                .sorted(Comparator.comparing(BatidaPonto::getDateTime))
                .collect(Collectors.groupingBy(
                        b -> b.getPis() + "_" + b.getDateTime().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (List<BatidaPonto> jornada : porPisEData.values()) {
            int     seqES  = 1;
            boolean entrada = true;

            for (int i = 0; i < jornada.size(); i++) {
                BatidaPonto batida    = jornada.get(i);
                boolean ultimaBatida = (i == jornada.size() - 1);
                boolean jornadaImpar = (jornada.size() % 2 != 0);
                boolean descartar    = ultimaBatida && jornadaImpar && entrada;

                String tipoMarcacao = descartar ? "D" : (entrada ? "E" : "S");
                String motivo = descartar
                        ? padDireita("MARCACAO SEM PAR - AGUARDANDO SAIDA", 100)
                        : padDireita("", 100);

                sb.append(gerarDetalhe(seq++, batida, tipoMarcacao, seqES, motivo))
                  .append("\n");
                totalDet++;

                if (!entrada || descartar) seqES++;
                if (!descartar) entrada = !entrada;
            }
        }

        sb.append(gerarTrailer(seq)).append("\n");

        String conteudo = sb.toString();
        log.info("AFDT gerado: {} registros.", totalDet);

        // ── 4. Salva no banco para histórico ──────────────────────────────
        salvarAfdt(request, conteudo, totalDet,
                dtInicial.toLocalDate(), dtFinal.toLocalDate());

        return conteudo;
    }

    // ── Montadores de linha ───────────────────────────────────────────────

    private String gerarCabecalho(int seq, LocalDateTime dtInicial,
                                   LocalDateTime dtFinal, LocalDateTime dtGeracao) {
        return padZero(seq, 9) + "1" + "1"
                + padZero(empresaConfig.getCnpj(), 14)
                + padZero(empresaConfig.getCei(), 12)
                + padDireita(empresaConfig.getRazaoSocial(), 150)
                + dtInicial.format(FMT_DATA)
                + dtFinal.format(FMT_DATA)
                + dtGeracao.format(FMT_DATA)
                + dtGeracao.format(FMT_HORA);
    }

    private String gerarDetalhe(int seq, BatidaPonto batida,
                                 String tipoMarcacao, int seqES, String motivo) {
        return padZero(seq, 9) + "2"
                + batida.getDateTime().format(FMT_DATA)
                + batida.getDateTime().format(FMT_HORA)
                + padZero(batida.getPis(), 12)
                + padZero(empresaConfig.getNumFabricacao(), 17)
                + tipoMarcacao
                + padZero(seqES, 2)
                + "O"
                + motivo;
    }

    private String gerarTrailer(int seq) {
        return padZero(seq, 9) + "9";
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Filtra batidas do banco conforme os parâmetros do request.
     */
    private List<BatidaPonto> buscarBatidasFiltradas(AfdtRequestDTO request) {
        // Usa variáveis final separadas para uso nas lambdas
        final LocalDate[] datas = new LocalDate[2]; // [0]=inicio, [1]=fim
    
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("ddMMyyyy");
            if (request.getDataInicial() != null && !request.getDataInicial().isBlank())
                datas[0] = LocalDate.parse(request.getDataInicial(), fmt);
            if (request.getDataFinal() != null && !request.getDataFinal().isBlank())
                datas[1] = LocalDate.parse(request.getDataFinal(), fmt);
        } catch (Exception e) {
            log.warn("Datas inválidas no request — buscando sem filtro de período.");
        }
    
        final LocalDate inicio = datas[0];
        final LocalDate fim    = datas[1];
    
        // Filtra por PIS se informado
        final String pis;
        if (request.getPis() != null && !request.getPis().isBlank()) {
            pis = String.format("%012d",
                    Long.parseLong(request.getPis().replaceAll("\\D", "")));
        } else {
            pis = null;
        }
    
        // Busca com filtros disponíveis
        if (pis != null && inicio != null && fim != null) {
            return batidaRepo.findByPisAndDateTimeBetweenOrderByDateTimeAsc(
                    pis,
                    inicio.atStartOfDay(),
                    fim.atTime(23, 59, 59));
        } else if (inicio != null && fim != null) {
            return batidaRepo.findAll().stream()
                    .filter(b -> !b.getDateTime().toLocalDate().isBefore(inicio)
                              && !b.getDateTime().toLocalDate().isAfter(fim))
                    .sorted(Comparator.comparing(BatidaPonto::getDateTime))
                    .toList();
        } else if (pis != null) {
            return batidaRepo.findByPisOrderByDateTimeAsc(pis);
        }
    
        return batidaRepo.findAll().stream()
                .sorted(Comparator.comparing(BatidaPonto::getDateTime))
                .toList();
    }

    /**
     * Salva o AFDT gerado no banco para histórico.
     */
    private void salvarAfdt(AfdtRequestDTO request, String conteudo,
                             int total, LocalDate inicio, LocalDate fim) {
        try {
            AfdtGerado entity = new AfdtGerado();
            entity.setDataGeracao(LocalDateTime.now());
            entity.setDataInicial(inicio);
            entity.setDataFinal(fim);
            entity.setPis(request.getPis());
            entity.setConteudo(conteudo);
            entity.setTotalRegistros(total);
            afdtRepo.save(entity);
            log.debug("AFDT salvo no banco com {} registros.", total);
        } catch (Exception e) {
            log.warn("Não foi possível salvar o AFDT no banco: {}", e.getMessage());
        }
    }

    private String padZero(long valor, int tamanho) {
        return String.format("%0" + tamanho + "d", valor);
    }

    private String padZero(String valor, int tamanho) {
        if (valor == null) valor = "";
        valor = valor.replaceAll("\\D", "");
        return String.format("%0" + tamanho + "d",
                valor.isEmpty() ? 0 : Long.parseLong(
                        valor.substring(0, Math.min(valor.length(), 18))));
    }

    private String padDireita(String valor, int tamanho) {
        if (valor == null) valor = "";
        if (valor.length() > tamanho) valor = valor.substring(0, tamanho);
        return String.format("%-" + tamanho + "s", valor);
    }
}