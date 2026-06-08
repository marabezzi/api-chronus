package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.config.AfdtEmpresaConfig;
import br.com.atom.api_chronus.config.LogoConfig;
import br.com.atom.api_chronus.dto.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gera PDFs do Relatório de Horas.
 *
 * Dois tipos:
 *   1. Todos os funcionários — tabela consolidada + totais
 *   2. Um funcionário — totais + semanal + diário
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioHorasPdfService {

    private final AfdtEmpresaConfig empresaConfig;
    private final LogoConfig        logoConfig;

    @Value("${app.timezone:America/Sao_Paulo}")
    private String timezone;

    // ── Cores ──────────────────────────────────────────────────────────────
    private static final Color AZUL_ESCURO = new Color(28,  57,  108);
    private static final Color AZUL_MEDIO  = new Color(70,  110, 180);
    private static final Color AZUL_CLARO  = new Color(220, 230, 245);
    private static final Color CINZA_CLARO = new Color(245, 245, 245);
    private static final Color CINZA_MEDIO = new Color(200, 200, 200);
    private static final Color VERMELHO    = new Color(180, 30,  30);
    private static final Color VERDE       = new Color(30,  120, 50);
    private static final Color BRANCO      = Color.WHITE;
    private static final Color PRETO       = Color.BLACK;

    // ── Fontes ─────────────────────────────────────────────────────────────
    private static final Font F_TITULO =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRANCO);
    private static final Font F_SUBTITULO =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRANCO);
    private static final Font F_LABEL =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, AZUL_ESCURO);
    private static final Font F_VALOR =
            FontFactory.getFont(FontFactory.HELVETICA, 8, PRETO);
    private static final Font F_CAB_TAB =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, BRANCO);
    private static final Font F_CAB_TAB_DARK =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, AZUL_ESCURO);
    private static final Font F_CELULA =
            FontFactory.getFont(FontFactory.HELVETICA, 7, PRETO);
    private static final Font F_CELULA_BOLD =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, PRETO);
    private static final Font F_INCOMPLETO =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, VERMELHO);
    private static final Font F_OK =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, VERDE);
    private static final Font F_TOTAL =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRANCO);
    private static final Font F_TOTAL_VAL =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRANCO);
    private static final Font F_ENTRADA =
            FontFactory.getFont(FontFactory.HELVETICA, 7, VERDE);
    private static final Font F_SAIDA =
            FontFactory.getFont(FontFactory.HELVETICA, 7, AZUL_MEDIO);

    // ─────────────────────────────────────────────────────────────────────
    // 1. TODOS OS FUNCIONÁRIOS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gera PDF consolidado de todos os funcionários.
     *
     * Layout:
     *   - Cabeçalho empresa + período
     *   - Tabela: Funcionário | Dias | Total Horas | Média Diária
     *   - Totalizadores gerais
     */
    public byte[] gerarTodos(RelatorioHorasResponseDTO rel) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 28, 28, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            Image logoEmpresa =
                    carregarImagem(logoConfig.getEmpresaPath(), 80, 40);
            Image logoChronus =
                    carregarImagem(logoConfig.getChronusPath(), 60, 25);

            writer.setPageEvent(new RodapePagina(
                    "Relatório de Horas — Todos os Funcionários",
                    logoChronus, timezone));
            doc.open();

            // Cabeçalho
            doc.add(gerarCabecalhoTodos(rel, logoEmpresa));
            doc.add(new Paragraph(" "));

            // Tabela consolidada
            doc.add(gerarTabelaTodos(rel));
            doc.add(new Paragraph(" "));

            // Totalizadores
            doc.add(gerarTotalizadores(
                    rel.getTotalFuncionarios(),
                    rel.getTotalGeralHoras(),
                    rel.getFuncionarios()));

            doc.close();
            log.info("PDF relatório todos gerado: {} a {}",
                    rel.getDataInicial(), rel.getDataFinal());
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Erro ao gerar PDF relatório todos: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. UM FUNCIONÁRIO
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gera PDF detalhado de um único funcionário.
     *
     * Layout:
     *   - Cabeçalho empresa + funcionário
     *   - Totalizadores individuais
     *   - Tabela semanal
     *   - Tabela diária
     */
    public byte[] gerarFuncionario(RelatorioHorasFuncionarioDTO rel,
                                    String dataInicial, String dataFinal) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 28, 28, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            Image logoEmpresa =
                    carregarImagem(logoConfig.getEmpresaPath(), 80, 40);
            Image logoChronus =
                    carregarImagem(logoConfig.getChronusPath(), 60, 25);

            writer.setPageEvent(new RodapePagina(
                    "Relatório de Horas — " + rel.getNome(),
                    logoChronus, timezone));
            doc.open();

            // Cabeçalho
            doc.add(gerarCabecalhoFuncionario(rel, dataInicial,
                    dataFinal, logoEmpresa));
            doc.add(new Paragraph(" "));

            // Totalizadores individuais
            doc.add(gerarTotalizadoresFuncionario(rel));
            doc.add(new Paragraph(" "));

            // Resumo semanal
            if (rel.getSemanas() != null && !rel.getSemanas().isEmpty()) {
                doc.add(gerarTabelaSemanal(rel.getSemanas()));
                doc.add(new Paragraph(" "));
            }

            // Detalhe diário
            if (rel.getDias() != null && !rel.getDias().isEmpty()) {
                doc.add(gerarTabelaDiaria(rel.getDias()));
            }

            doc.close();
            log.info("PDF relatório funcionário gerado: {} | {} a {}",
                    rel.getNome(), dataInicial, dataFinal);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Erro ao gerar PDF funcionário: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÕES DO PDF — TODOS
    // ─────────────────────────────────────────────────────────────────────

    private PdfPTable gerarCabecalhoTodos(
            RelatorioHorasResponseDTO rel, Image logoEmpresa)
            throws DocumentException {

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.5f, 6.5f});

        // Logo
        PdfPCell cLogo = logoEmpresa != null
                ? new PdfPCell(logoEmpresa, true)
                : celula("LOGO", F_LABEL, Element.ALIGN_CENTER);
        cLogo.setBackgroundColor(AZUL_CLARO);
        cLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
        cLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cLogo.setPadding(8);
        cLogo.setRowspan(2);
        t.addCell(cLogo);

        // Título
        PdfPCell titulo = celula(
                "RELATORIO DE HORAS POR FUNCIONARIO",
                F_TITULO, Element.ALIGN_CENTER);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(7);
        titulo.setBorder(0);
        t.addCell(titulo);

        // Dados
        PdfPTable dados = new PdfPTable(4);
        dados.setWidthPercentage(100);
        dados.setWidths(new float[]{1.2f, 3f, 1.2f, 3f});

        addDado(dados, "Empregador:", empresaConfig.getRazaoSocial());
        addDado(dados, "CNPJ:",       formatCnpj(empresaConfig.getCnpj()));
        addDado(dados, "Periodo:",
                formatData(rel.getDataInicial())
                + " a " + formatData(rel.getDataFinal()));
        addDado(dados, "Emitido em:", agora(timezone));

        PdfPCell cDados = new PdfPCell(dados);
        cDados.setBackgroundColor(AZUL_CLARO);
        cDados.setPadding(5);
        cDados.setBorderColor(AZUL_ESCURO);
        cDados.setBorderWidth(1f);
        t.addCell(cDados);

        return t;
    }

    private PdfPTable gerarTabelaTodos(RelatorioHorasResponseDTO rel)
            throws DocumentException {

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{4f, 1.5f, 1.5f, 1.5f});

        // Título
        PdfPCell titulo = new PdfPCell(
                new Phrase("RESUMO POR FUNCIONARIO", F_SUBTITULO));
        titulo.setColspan(4);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(5);
        titulo.setBorderColor(BRANCO);
        t.addCell(titulo);

        // Cabeçalhos
        for (String cab : new String[]{
                "FUNCIONARIO", "DIAS COM PONTO",
                "TOTAL HORAS", "MEDIA DIARIA"}) {
            PdfPCell c = celula(cab, F_CAB_TAB_DARK, Element.ALIGN_CENTER);
            c.setBackgroundColor(AZUL_CLARO);
            c.setPadding(5);
            c.setBorderColor(CINZA_MEDIO);
            t.addCell(c);
        }

        // Linhas
        boolean alt = false;
        for (RelatorioHorasFuncionarioDTO func : rel.getFuncionarios()) {
            Color bg = alt ? CINZA_CLARO : BRANCO;

            addCelulaLinha(t, func.getNome(),
                    F_CELULA_BOLD, Element.ALIGN_LEFT, bg);
            addCelulaLinha(t, String.valueOf(func.getTotalDias()),
                    F_CELULA, Element.ALIGN_CENTER, bg);
            addCelulaLinha(t, func.getTotalHoras(),
                    F_CELULA_BOLD, Element.ALIGN_CENTER, bg);
            addCelulaLinha(t, func.getMediaDiaria(),
                    F_CELULA, Element.ALIGN_CENTER, bg);

            alt = !alt;
        }

        return t;
    }

    private PdfPTable gerarTotalizadores(int totalFunc,
                                          String totalHoras,
                                          List<RelatorioHorasFuncionarioDTO> funcs)
            throws DocumentException {

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);

        // Calcula média geral
        int totalMin = 0;
        for (RelatorioHorasFuncionarioDTO f : funcs) {
            String[] p = f.getTotalHoras().split(":");
            totalMin += Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        }
        int mediaMin = totalFunc > 0 ? totalMin / totalFunc : 0;
        String mediaGeral = String.format("%02d:%02d",
                mediaMin / 60, mediaMin % 60);

        addTotalBox(t, "FUNCIONARIOS",
                String.valueOf(totalFunc), AZUL_ESCURO);
        addTotalBox(t, "TOTAL GERAL DE HORAS",
                totalHoras, VERDE);
        addTotalBox(t, "MEDIA POR FUNCIONARIO",
                mediaGeral, AZUL_MEDIO);

        return t;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÕES DO PDF — UM FUNCIONÁRIO
    // ─────────────────────────────────────────────────────────────────────

    private PdfPTable gerarCabecalhoFuncionario(
            RelatorioHorasFuncionarioDTO rel,
            String dataInicial, String dataFinal,
            Image logoEmpresa) throws DocumentException {

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.5f, 6.5f});

        PdfPCell cLogo = logoEmpresa != null
                ? new PdfPCell(logoEmpresa, true)
                : celula("LOGO", F_LABEL, Element.ALIGN_CENTER);
        cLogo.setBackgroundColor(AZUL_CLARO);
        cLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
        cLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cLogo.setPadding(8);
        cLogo.setRowspan(2);
        t.addCell(cLogo);

        PdfPCell titulo = celula(
                "RELATORIO DE HORAS — FUNCIONARIO",
                F_TITULO, Element.ALIGN_CENTER);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(7);
        titulo.setBorder(0);
        t.addCell(titulo);

        PdfPTable dados = new PdfPTable(4);
        dados.setWidthPercentage(100);
        dados.setWidths(new float[]{1.2f, 3f, 1.2f, 3f});

        addDado(dados, "Empregador:", empresaConfig.getRazaoSocial());
        addDado(dados, "CNPJ:",       formatCnpj(empresaConfig.getCnpj()));
        addDado(dados, "Funcionario:", rel.getNome());
        addDado(dados, "PIS:",         rel.getPis());
        addDado(dados, "Periodo:",
                formatDataDDMM(dataInicial)
                + " a " + formatDataDDMM(dataFinal));
        addDado(dados, "Emitido em:", agora(timezone));

        PdfPCell cDados = new PdfPCell(dados);
        cDados.setBackgroundColor(AZUL_CLARO);
        cDados.setPadding(5);
        cDados.setBorderColor(AZUL_ESCURO);
        cDados.setBorderWidth(1f);
        t.addCell(cDados);

        return t;
    }

    private PdfPTable gerarTotalizadoresFuncionario(
            RelatorioHorasFuncionarioDTO rel) throws DocumentException {

        PdfPTable t = new PdfPTable(3);
        t.setWidthPercentage(100);

        addTotalBox(t, "DIAS COM PONTO",
                String.valueOf(rel.getTotalDias()), AZUL_ESCURO);
        addTotalBox(t, "TOTAL DE HORAS",
                rel.getTotalHoras(), VERDE);
        addTotalBox(t, "MEDIA DIARIA",
                rel.getMediaDiaria(), AZUL_MEDIO);

        return t;
    }

    private PdfPTable gerarTabelaSemanal(
            List<EspelhoSemanaDTO> semanas) throws DocumentException {

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{3.5f, 1.5f, 1.5f, 1.5f});

        PdfPCell titulo = new PdfPCell(
                new Phrase("RESUMO SEMANAL", F_SUBTITULO));
        titulo.setColspan(4);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(5);
        titulo.setBorderColor(BRANCO);
        t.addCell(titulo);

        for (String cab : new String[]{
                "SEMANA", "DIAS", "TOTAL HORAS", "MEDIA DIARIA"}) {
            PdfPCell c = celula(cab, F_CAB_TAB_DARK, Element.ALIGN_CENTER);
            c.setBackgroundColor(AZUL_CLARO);
            c.setPadding(4);
            c.setBorderColor(CINZA_MEDIO);
            t.addCell(c);
        }

        boolean alt = false;
        for (EspelhoSemanaDTO s : semanas) {
            Color bg = alt ? CINZA_CLARO : BRANCO;
            addCelulaLinha(t, s.getSemana(),
                    F_CELULA_BOLD, Element.ALIGN_LEFT, bg);
            addCelulaLinha(t, String.valueOf(s.getDiasTrabalhados()),
                    F_CELULA, Element.ALIGN_CENTER, bg);
            addCelulaLinha(t, s.getTotalHoras(),
                    F_CELULA_BOLD, Element.ALIGN_CENTER, bg);
            addCelulaLinha(t, s.getMediaDiaria(),
                    F_CELULA, Element.ALIGN_CENTER, bg);
            alt = !alt;
        }

        return t;
    }

    private PdfPTable gerarTabelaDiaria(
            List<EspelhoDiaDTO> dias) throws DocumentException {

        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1f, 1.5f, 3.5f, 1.2f, 1f});

        PdfPCell titulo = new PdfPCell(
                new Phrase("DETALHE DIARIO", F_SUBTITULO));
        titulo.setColspan(5);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(5);
        titulo.setBorderColor(BRANCO);
        t.addCell(titulo);

        for (String cab : new String[]{
                "DATA", "DIA SEMANA", "MARCACOES", "TOTAL", "STATUS"}) {
            PdfPCell c = celula(cab, F_CAB_TAB_DARK, Element.ALIGN_CENTER);
            c.setBackgroundColor(AZUL_CLARO);
            c.setPadding(4);
            c.setBorderColor(CINZA_MEDIO);
            t.addCell(c);
        }

        boolean alt = false;
        for (EspelhoDiaDTO dia : dias) {
            Color bg = alt ? CINZA_CLARO : BRANCO;

            Font fDia = dia.isTemInconsistencia() ? F_INCOMPLETO : F_CELULA_BOLD;
            addCelulaLinha(t, formatDia(dia.getData()),
                    fDia, Element.ALIGN_CENTER, bg);
            addCelulaLinha(t, capitalize(dia.getDiaSemana()),
                    F_CELULA, Element.ALIGN_CENTER, bg);

            // Marcações na mesma célula
            StringBuilder marcStr = new StringBuilder();
            for (int i = 0; i < dia.getMarcacoes().size(); i++) {
                if (i > 0) marcStr.append("  ");
                marcStr.append(dia.getMarcacoes().get(i).getHorario());
            }
            addCelulaLinha(t, marcStr.toString(),
                    F_CELULA, Element.ALIGN_CENTER, bg);

            Font fTot = dia.isTemInconsistencia() ? F_INCOMPLETO : F_OK;
            addCelulaLinha(t, dia.getTotalTrabalhado()
                    + (dia.isTemInconsistencia() ? " !" : ""),
                    fTot, Element.ALIGN_CENTER, bg);

            String status = dia.isTemInconsistencia() ? "INCOMPLETO" : "OK";
            addCelulaLinha(t, status, fTot, Element.ALIGN_CENTER, bg);

            alt = !alt;
        }

        return t;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private PdfPCell celula(String texto, Font font, int align) {
        PdfPCell c = new PdfPCell(
                new Phrase(texto != null ? texto : "", font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private void addDado(PdfPTable t, String label, String valor) {
        PdfPCell cl = celula(label, F_LABEL, Element.ALIGN_RIGHT);
        cl.setBorder(Rectangle.NO_BORDER);
        cl.setPadding(2);
        t.addCell(cl);
        PdfPCell cv = celula(valor, F_VALOR, Element.ALIGN_LEFT);
        cv.setBorder(Rectangle.NO_BORDER);
        cv.setPadding(2);
        t.addCell(cv);
    }

    private void addCelulaLinha(PdfPTable t, String texto,
                                 Font font, int align, Color bg) {
        PdfPCell c = celula(texto, font, align);
        c.setBackgroundColor(bg);
        c.setPadding(4);
        c.setBorderColor(CINZA_MEDIO);
        t.addCell(c);
    }

    private void addTotalBox(PdfPTable t, String label,
                              String valor, Color bg) {
        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);

        PdfPCell cl = celula(label, F_TOTAL, Element.ALIGN_CENTER);
        cl.setBackgroundColor(bg);
        cl.setBorder(Rectangle.NO_BORDER);
        cl.setPaddingTop(8);
        cl.setPaddingBottom(2);
        inner.addCell(cl);

        PdfPCell cv = celula(valor, F_TOTAL_VAL, Element.ALIGN_CENTER);
        cv.setBackgroundColor(bg);
        cv.setBorder(Rectangle.NO_BORDER);
        cv.setPaddingBottom(10);
        inner.addCell(cv);

        PdfPCell c = new PdfPCell(inner);
        c.setPadding(0);
        c.setBorderColor(BRANCO);
        c.setBorderWidth(2);
        t.addCell(c);
    }

    private Image carregarImagem(String caminho,
                                  float largura, float altura) {
        try {
            File f = new File(caminho);
            if (!f.exists()) return null;
            Image img = Image.getInstance(caminho);
            img.scaleToFit(largura, altura);
            return img;
        } catch (Exception e) {
            log.warn("Erro ao carregar logo {}: {}", caminho, e.getMessage());
            return null;
        }
    }

    private String agora(String tz) {
        return ZonedDateTime.now(ZoneId.of(tz))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String formatData(String iso) {
        if (iso == null || iso.length() < 10) return iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7)
                + "/" + iso.substring(0, 4);
    }

    /** Converte ddMMyyyy para dd/MM/yyyy */
    private String formatDataDDMM(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.length() != 8) return ddMMyyyy;
        return ddMMyyyy.substring(0, 2) + "/" + ddMMyyyy.substring(2, 4)
                + "/" + ddMMyyyy.substring(4);
    }

    private String formatDia(String iso) {
        if (iso == null || iso.length() < 10) return iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7);
    }

    private String formatCnpj(String c) {
        if (c == null || c.length() != 14) return c;
        return c.substring(0, 2) + "." + c.substring(2, 5) + "."
                + c.substring(5, 8) + "/" + c.substring(8, 12)
                + "-" + c.substring(12);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ── Rodapé ────────────────────────────────────────────────────────────

    private static class RodapePagina extends PdfPageEventHelper {

        private final String descricao;
        private final Image  logoChronus;
        private final String timezone;

        RodapePagina(String desc, Image logo, String tz) {
            this.descricao   = desc;
            this.logoChronus = logo;
            this.timezone    = tz;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            try {
                PdfContentByte cb = writer.getDirectContent();

                if (logoChronus != null) {
                    logoChronus.setAbsolutePosition(doc.left(),
                            doc.bottom() - 20);
                    cb.addImage(logoChronus);
                }

                String agora = ZonedDateTime.now(ZoneId.of(timezone))
                        .format(DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy HH:mm"));

                Font f = FontFactory.getFont(
                        FontFactory.HELVETICA, 6, Color.GRAY);

                Phrase p = new Phrase(
                        "Chronus  |  " + descricao
                        + "  |  Emitido em: " + agora
                        + "  |  Pag. " + writer.getPageNumber(), f);

                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, p,
                        (doc.left() + doc.right()) / 2,
                        doc.bottom() - 15, 0);

            } catch (Exception ignored) {}
        }
    }
}