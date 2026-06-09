package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.config.AfdtEmpresaConfig;
import br.com.atom.api_chronus.config.LogoConfig;
import br.com.atom.api_chronus.dto.EspelhoDiaDTO;
import br.com.atom.api_chronus.dto.EspelhoMarcacaoDTO;
import br.com.atom.api_chronus.dto.EspelhoResponseDTO;

import java.util.List;
import br.com.atom.api_chronus.repository.UsuarioPontoRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gera o PDF do Espelho de Ponto FIEL ao Anexo II da Portaria 1510/MTE.
 *
 * Diferenças do EspelhoPdfService existente:
 *   - Estrutura exata do Anexo II: colunas conforme o modelo oficial
 *   - Campo "Admissão" preenchido com data real do cadastro
 *   - Colunas "Jornada Realizada" com 3 pares E/S (conforme o modelo)
 *   - Coluna "CH" (Código de Horário)
 *   - Coluna "Tratamentos": Horário | Ocor. (D/I/P) | Motivo
 *   - Layout visual mais próximo do modelo impresso oficial
 *
 * Endpoint: POST /api/mte/espelho/pis/pdf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspelhoMtePdfService {

    private final AfdtEmpresaConfig      empresaConfig;
    private final LogoConfig             logoConfig;
    private final UsuarioPontoRepository usuarioRepo;

    @Value("${app.timezone:America/Sao_Paulo}")
    private String timezone;

    // ── Cores ──────────────────────────────────────────────────────────────
    private static final Color AZUL    = new Color(28,  57,  108);
    private static final Color CINZA   = new Color(220, 220, 220);
    private static final Color BRANCO  = Color.WHITE;
    private static final Color PRETO   = Color.BLACK;
    private static final Color VERMELHO= new Color(180, 30, 30);

    // ── Fontes ─────────────────────────────────────────────────────────────
    private static final Font F_TITULO =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRANCO);
    private static final Font F_BOLD_8 =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, PRETO);
    private static final Font F_BOLD_7 =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, PRETO);
    private static final Font F_NORM_8 =
            FontFactory.getFont(FontFactory.HELVETICA, 8, PRETO);
    private static final Font F_NORM_7 =
            FontFactory.getFont(FontFactory.HELVETICA, 7, PRETO);
    private static final Font F_BRANCO_7 =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, BRANCO);
    private static final Font F_VERM_7 =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, VERMELHO);
    private static final Font F_VERDE_7 =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7,
                    new Color(30, 100, 40));

    // ─────────────────────────────────────────────────────────────────────
    // MÉTODO PRINCIPAL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gera o PDF do Espelho de Ponto conforme Anexo II / Portaria 1510.
     *
     * @param espelho    dados do espelho
     * @param dataInicial ddMMyyyy
     * @param dataFinal   ddMMyyyy
     */
    public byte[] gerar(EspelhoResponseDTO espelho,
                         String dataInicial, String dataFinal) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 25, 25, 40, 35);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            Image logoEmpresa =
                    carregarImagem(logoConfig.getEmpresaPath(), 70, 35);
            Image logoChronus =
                    carregarImagem(logoConfig.getChronusPath(), 55, 22);

            writer.setPageEvent(new RodapeMte(espelho.getNome(),
                    logoChronus, timezone));
            doc.open();

            // 1. Cabeçalho
            doc.add(gerarCabecalho(espelho, dataInicial,
                    dataFinal, logoEmpresa));
            doc.add(Chunk.NEWLINE);

            // 2. Horários contratuais
            doc.add(gerarHorariosContratuais());
            doc.add(Chunk.NEWLINE);

            // 3. Período
            doc.add(gerarPeriodo(dataInicial, dataFinal));
            doc.add(Chunk.NEWLINE);

            // 4. Tabela principal — fiel ao Anexo II
            doc.add(gerarTabelaPrincipal(espelho));
            doc.add(Chunk.NEWLINE);

            // 5. Totalizadores
            doc.add(gerarTotalizadores(espelho));
            doc.add(Chunk.NEWLINE);

            // 6. Assinaturas
            doc.add(gerarAssinaturas());

            doc.close();
            log.info("PDF MTE gerado: {} | {} a {}",
                    espelho.getNome(), dataInicial, dataFinal);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Erro ao gerar PDF MTE: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÃO 1 — CABEÇALHO
    // ─────────────────────────────────────────────────────────────────────

    private PdfPTable gerarCabecalho(EspelhoResponseDTO espelho,
                                      String dataInicial, String dataFinal,
                                      Image logoEmpresa)
            throws DocumentException {

        // Busca data de admissão do banco
        String admissao = buscarDataAdmissao(espelho.getPis());

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.5f, 6.5f});

        // Logo
        PdfPCell cLogo = logoEmpresa != null
                ? new PdfPCell(logoEmpresa, true)
                : celula("LOGO", F_BOLD_8, Element.ALIGN_CENTER);
        cLogo.setBackgroundColor(new Color(232, 240, 254));
        cLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
        cLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cLogo.setPadding(6);
        cLogo.setRowspan(2);
        t.addCell(cLogo);

        // Título oficial
        PdfPCell titulo = celula(
                "Relatorio Espelho de Ponto Eletronico",
                F_TITULO, Element.ALIGN_CENTER);
        titulo.setBackgroundColor(AZUL);
        titulo.setPadding(6);
        titulo.setBorder(0);
        t.addCell(titulo);

        // Dados conforme Anexo II
        PdfPTable dados = new PdfPTable(2);
        dados.setWidthPercentage(100);
        dados.setWidths(new float[]{1.5f, 5f});

        addLinha(dados, "Empregador:",
                empresaConfig.getRazaoSocial()
                + " — CNPJ: " + formatCnpj(empresaConfig.getCnpj()));
        addLinha(dados, "Endereco:",
                nvl(empresaConfig.getLocal()));
        addLinha(dados, "Empregado:",
                espelho.getPis() + " — " + espelho.getNome());
        addLinha(dados, "Admissao:", admissao);
        addLinha(dados, "Relatorio emitido em:", agora(timezone));

        PdfPCell cDados = new PdfPCell(dados);
        cDados.setBackgroundColor(new Color(245, 247, 252));
        cDados.setPadding(4);
        cDados.setBorderColor(AZUL);
        cDados.setBorderWidth(0.5f);
        t.addCell(cDados);

        return t;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÃO 2 — HORÁRIOS CONTRATUAIS
    // ─────────────────────────────────────────────────────────────────────

    private PdfPTable gerarHorariosContratuais() throws DocumentException {
        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{2f, 1.5f, 1.5f, 1.5f, 1.5f});

        // Título
        PdfPCell h = new PdfPCell(new Phrase(
                "Horarios contratuais do empregado:", F_BOLD_8));
        h.setColspan(5);
        h.setBackgroundColor(CINZA);
        h.setPadding(4);
        h.setBorderColor(AZUL);
        t.addCell(h);

        // Cabeçalho
        for (String cab : new String[]{
                "Codigo de Horario (CH)",
                "Entrada", "Saida", "Entrada", "Saida"}) {
            PdfPCell c = celula(cab, F_BRANCO_7, Element.ALIGN_CENTER);
            c.setBackgroundColor(AZUL);
            c.setPadding(3);
            t.addCell(c);
        }

        // 3 linhas vazias (CH não gerenciado pelo REP)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 5; j++) {
                PdfPCell c = celula("", F_NORM_7, Element.ALIGN_CENTER);
                c.setBackgroundColor(i % 2 == 0 ? BRANCO : CINZA);
                c.setMinimumHeight(12f);
                c.setPadding(2);
                t.addCell(c);
            }
        }

        return t;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÃO 3 — PERÍODO
    // ─────────────────────────────────────────────────────────────────────

    private PdfPTable gerarPeriodo(String di, String df)
            throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.2f, 5f});

        PdfPCell label = celula("Periodo:", F_BOLD_8, Element.ALIGN_LEFT);
        label.setBackgroundColor(CINZA);
        label.setPadding(4);
        label.setBorderColor(AZUL);
        t.addCell(label);

        PdfPCell val = celula(
                formatDataDDMM(di) + " a " + formatDataDDMM(df),
                F_NORM_8, Element.ALIGN_LEFT);
        val.setBackgroundColor(CINZA);
        val.setPadding(4);
        val.setBorderColor(AZUL);
        t.addCell(val);

        return t;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÃO 4 — TABELA PRINCIPAL (fiel ao Anexo II)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Tabela conforme Anexo II exato:
     *
     * Dia | Marcações brutas | E1 S1 E2 S2 E3 S3 | CH | Hor. Ocor. Motivo
     *
     * Total: 13 colunas
     */
    private PdfPTable gerarTabelaPrincipal(EspelhoResponseDTO espelho)
            throws DocumentException {

        PdfPTable t = new PdfPTable(13);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{
                0.9f,  // Dia
                2.8f,  // Marcações brutas (todos hh:mm juntos)
                0.8f, 0.8f,  // Jornada E1 S1
                0.8f, 0.8f,  // Jornada E2 S2
                0.8f, 0.8f,  // Jornada E3 S3
                0.6f,        // CH
                0.8f,        // Trat. Horário
                0.5f,        // Trat. Ocor.
                2.0f,        // Trat. Motivo
                0.9f         // Total
        });

        // ── Linha de grupos ───────────────────────────────────────────────
        cabGrupo(t, "Dia",               1, AZUL);
        cabGrupo(t, "Marcacoes registradas no ponto eletronico", 1,
                new Color(50, 90, 160));
        cabGrupo(t, "Jornada realizada", 6, AZUL);
        cabGrupo(t, "CH",                1, new Color(50, 90, 160));
        cabGrupo(t, "Tratamentos efetuados sobre os dados originais",
                3, AZUL);
        cabGrupo(t, "Total",             1, new Color(50, 90, 160));

        // ── Sub-cabeçalhos ────────────────────────────────────────────────
        subCab(t, "dd");
        subCab(t, "hh:mm  hh:mm  hh:mm  hh:mm");
        subCab(t, "Ent."); subCab(t, "Sai.");
        subCab(t, "Ent."); subCab(t, "Sai.");
        subCab(t, "Ent."); subCab(t, "Sai.");
        subCab(t, "CH");
        subCab(t, "Horario");
        subCab(t, "Ocor.");
        subCab(t, "Motivo");
        subCab(t, "HH:mm");

        // ── Linhas de dados ───────────────────────────────────────────────
        boolean alt = false;
        for (EspelhoDiaDTO dia : espelho.getDias()) {
            Color bg = alt ? CINZA : BRANCO;
            List<EspelhoMarcacaoDTO> ms = dia.getMarcacoes();

            // Dia
            Font fDia = dia.isTemInconsistencia() ? F_VERM_7 : F_BOLD_7;
            lin(t, formatDia(dia.getData()), fDia,
                    Element.ALIGN_CENTER, bg);

            // Marcações brutas — todas na mesma célula
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ms.size(); i++) {
                if (i > 0) sb.append("  ");
                sb.append(ms.get(i).getHorario());
            }
            lin(t, sb.toString(), F_NORM_7, Element.ALIGN_CENTER, bg);

            // Jornada E/S — 3 pares (6 slots)
            String[] jornada = new String[6];
            int ej = 0, sj = 0;
            for (EspelhoMarcacaoDTO m : ms) {
                if (m.getTipo().startsWith("Entrada") && ej < 3) {
                    jornada[ej * 2] = m.getHorario();
                    ej++;
                } else if (m.getTipo().startsWith("Sa") && sj < 3) {
                    jornada[sj * 2 + 1] = m.getHorario();
                    sj++;
                }
            }
            for (int i = 0; i < 6; i++) {
                String v = jornada[i] != null ? jornada[i] : "";
                Font f = (i % 2 == 0)
                        ? FontFactory.getFont(FontFactory.HELVETICA, 7,
                                new Color(30, 100, 40))
                        : FontFactory.getFont(FontFactory.HELVETICA, 7,
                                new Color(50, 90, 160));
                lin(t, v, f, Element.ALIGN_CENTER, bg);
            }

            // CH (não disponível)
            lin(t, "-", F_NORM_7, Element.ALIGN_CENTER, bg);

            // Tratamentos: Horário | Ocor. | Motivo
            if (dia.isTemInconsistencia()) {
                // Última marcação sem par → desconsiderada (D)
                String ultHora = ms.isEmpty() ? ""
                        : ms.get(ms.size() - 1).getHorario();
                lin(t, ultHora, F_VERM_7, Element.ALIGN_CENTER, bg);
                lin(t, "D",     F_VERM_7, Element.ALIGN_CENTER, bg);
                lin(t, "Marcacao sem par",
                        F_VERM_7, Element.ALIGN_LEFT, bg);
            } else {
                lin(t, "", F_NORM_7, Element.ALIGN_CENTER, bg);
                lin(t, "", F_NORM_7, Element.ALIGN_CENTER, bg);
                lin(t, "", F_NORM_7, Element.ALIGN_LEFT,  bg);
            }

            // Total trabalhado
            Font fTot = dia.isTemInconsistencia() ? F_VERM_7 : F_VERDE_7;
            lin(t, dia.getTotalTrabalhado(), fTot,
                    Element.ALIGN_CENTER, bg);

            alt = !alt;
        }

        return t;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÃO 5 — TOTALIZADORES
    // ─────────────────────────────────────────────────────────────────────
    @SuppressWarnings("null")
    private PdfPTable gerarTotalizadores(EspelhoResponseDTO espelho)
            throws DocumentException {

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);

        long inc = espelho.getDias().stream()
                .filter(EspelhoDiaDTO::isTemInconsistencia).count();

        totalBox(t, "DIAS COM PONTO",
                String.valueOf(espelho.getTotalDiasComPonto()),
                AZUL);
        totalBox(t, "TOTAL DE HORAS",
                espelho.getTotalHorasTrabalhadas(),
                new Color(20, 100, 40));
        totalBox(t, "DIAS INCOMPLETOS",
                String.valueOf(inc),
                inc > 0 ? VERMELHO : new Color(80, 80, 80));
        totalBox(t, "MEDIA DIARIA",
                calcMedia(espelho),
                new Color(50, 90, 160));

        return t;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÃO 6 — ASSINATURAS
    // ─────────────────────────────────────────────────────────────────────

    private PdfPTable gerarAssinaturas() throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);

        PdfPCell titulo = new PdfPCell(
                new Phrase("ASSINATURAS", F_BRANCO_7));
        titulo.setColspan(2);
        titulo.setBackgroundColor(AZUL);
        titulo.setPadding(4);
        titulo.setBorderColor(BRANCO);
        t.addCell(titulo);

        t.addCell(celulaAssinatura("Assinatura do Empregado:"));
        t.addCell(celulaAssinatura("Assinatura do Responsavel pelo Setor:"));

        return t;
    }

    private PdfPCell celulaAssinatura(String label) {
        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);

        PdfPCell cl = celula(label, F_BOLD_8, Element.ALIGN_LEFT);
        cl.setBorder(Rectangle.NO_BORDER);
        cl.setPaddingTop(8);
        cl.setPaddingLeft(10);
        cl.setPaddingBottom(4);
        inner.addCell(cl);

        PdfPCell linha = celula("", F_NORM_7, Element.ALIGN_LEFT);
        linha.setBorder(Rectangle.BOTTOM);
        linha.setBorderColor(PRETO);
        linha.setBorderWidth(0.5f);
        linha.setMinimumHeight(30f);
        linha.setPaddingLeft(10f);
        linha.setPaddingRight(10f);
        inner.addCell(linha);

        PdfPCell esp = celula("", F_NORM_7, Element.ALIGN_LEFT);
        esp.setBorder(Rectangle.NO_BORDER);
        esp.setMinimumHeight(8f);
        inner.addCell(esp);

        PdfPCell cell = new PdfPCell(inner);
        cell.setPadding(4);
        cell.setBorderColor(new Color(200, 200, 200));
        return cell;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private PdfPCell celula(String txt, Font font, int align) {
        PdfPCell c = new PdfPCell(
                new Phrase(txt != null ? txt : "", font));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private void addLinha(PdfPTable t, String label, String valor) {
        PdfPCell cl = celula(label, F_BOLD_8, Element.ALIGN_RIGHT);
        cl.setBorder(Rectangle.NO_BORDER);
        cl.setPadding(2);
        t.addCell(cl);
        PdfPCell cv = celula(valor, F_NORM_8, Element.ALIGN_LEFT);
        cv.setBorder(Rectangle.NO_BORDER);
        cv.setPadding(2);
        t.addCell(cv);
    }

    private void cabGrupo(PdfPTable t, String txt,
                           int colspan, Color bg) {
        PdfPCell c = celula(txt, F_BRANCO_7, Element.ALIGN_CENTER);
        c.setColspan(colspan);
        c.setBackgroundColor(bg);
        c.setPadding(3);
        c.setBorderColor(BRANCO);
        t.addCell(c);
    }

    private void subCab(PdfPTable t, String txt) {
        PdfPCell c = celula(txt, F_BOLD_7, Element.ALIGN_CENTER);
        c.setBackgroundColor(CINZA);
        c.setPadding(2);
        c.setBorderColor(new Color(180, 180, 180));
        t.addCell(c);
    }

    private void lin(PdfPTable t, String txt, Font font,
                      int align, Color bg) {
        PdfPCell c = celula(txt, font, align);
        c.setBackgroundColor(bg);
        c.setPadding(2);
        c.setBorderColor(new Color(200, 200, 200));
        t.addCell(c);
    }

    private void totalBox(PdfPTable t, String label,
                           String valor, Color bg) {
        Font fL = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 8, BRANCO);
        Font fV = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 14, BRANCO);

        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);

        PdfPCell cl = celula(label, fL, Element.ALIGN_CENTER);
        cl.setBackgroundColor(bg);
        cl.setBorder(Rectangle.NO_BORDER);
        cl.setPaddingTop(6);
        cl.setPaddingBottom(1);
        inner.addCell(cl);

        PdfPCell cv = celula(valor, fV, Element.ALIGN_CENTER);
        cv.setBackgroundColor(bg);
        cv.setBorder(Rectangle.NO_BORDER);
        cv.setPaddingBottom(8);
        inner.addCell(cv);

        PdfPCell c = new PdfPCell(inner);
        c.setPadding(0);
        c.setBorderColor(BRANCO);
        c.setBorderWidth(2);
        t.addCell(c);
    }

    /**
     * Busca a data de admissão do funcionário no banco.
     * Retorna "—" se não cadastrada.
     */
    private String buscarDataAdmissao(String pis) {
        return usuarioRepo.findByPisFormatado(pis)
                .filter(u -> u.getDataAdmissao() != null)
                .map(u -> u.getDataAdmissao()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .orElse("-");
    }

    private Image carregarImagem(String caminho,
                                  float w, float h) {
        try {
            if (caminho == null || !new File(caminho).exists())
                return null;
            Image img = Image.getInstance(caminho);
            img.scaleToFit(w, h);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    private String agora(String tz) {
        return ZonedDateTime.now(ZoneId.of(tz))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String formatDataDDMM(String ddMMyyyy) {
        if (ddMMyyyy == null || ddMMyyyy.length() != 8) return ddMMyyyy;
        return ddMMyyyy.substring(0, 2) + "/"
                + ddMMyyyy.substring(2, 4) + "/"
                + ddMMyyyy.substring(4);
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

    private String nvl(String s) { return s != null ? s : "-"; }

    private String calcMedia(EspelhoResponseDTO e) {
        if (e.getTotalDiasComPonto() == 0) return "00:00";
        String[] p = e.getTotalHorasTrabalhadas().split(":");
        int tot = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        int med = tot / e.getTotalDiasComPonto();
        return String.format("%02d:%02d", med / 60, med % 60);
    }

    // ─────────────────────────────────────────────────────────────────────
    // RODAPÉ
    // ─────────────────────────────────────────────────────────────────────

    private static class RodapeMte extends PdfPageEventHelper {

        private final String nome;
        private final Image  logo;
        private final String tz;

        RodapeMte(String n, Image l, String t) {
            nome = n; logo = l; tz = t;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            try {
                PdfContentByte cb = writer.getDirectContent();

                if (logo != null) {
                    logo.setAbsolutePosition(doc.left(),
                            doc.bottom() - 18);
                    cb.addImage(logo);
                }

                String agora = ZonedDateTime.now(ZoneId.of(tz))
                        .format(DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy HH:mm"));

                Font f = FontFactory.getFont(
                        FontFactory.HELVETICA, 6, Color.GRAY);
                Phrase p = new Phrase(
                        "Chronus MTE  |  Portaria 1510/MTE - Anexo II"
                        + "  |  " + nome
                        + "  |  Emitido em: " + agora
                        + "  |  Pag. " + writer.getPageNumber(), f);

                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, p,
                        (doc.left() + doc.right()) / 2,
                        doc.bottom() - 12, 0);

            } catch (Exception ignored) {}
        }
    }

    /**
         * Gera um PDF único com o espelho de todos os funcionários.
         * Cada funcionário ocupa suas próprias páginas.
         * Usa PdfCopy para concatenar os PDFs individuais.
         *
         * @param espelhos lista de espelhos (um por funcionário)
         * @param di       dataInicial ddMMyyyy
         * @param df       dataFinal   ddMMyyyy
         * @return bytes do PDF consolidado
     */
       public byte[] gerarTodos(List<EspelhoResponseDTO> espelhos,
                          String di, String df) {
    if (espelhos == null || espelhos.isEmpty()) return null;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document doc = new Document();

    try (PdfCopy copy = new PdfCopy(doc, out)) {
        doc.open();

        int gerados = 0;
        for (EspelhoResponseDTO espelho : espelhos) {
            if (espelho == null || espelho.getDias() == null
                    || espelho.getDias().isEmpty()) continue;

            byte[] individual = gerar(espelho, di, df);
            if (individual == null) continue;

            PdfReader reader = new PdfReader(individual);
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                copy.addPage(copy.getImportedPage(reader, p));
            }
            reader.close();
            gerados++;
        }

        doc.close();

        if (gerados == 0) return null;

        log.info("PDF todos gerado: {} espelhos | {} a {}",
                gerados, di, df);
        return out.toByteArray();

    } catch (Exception e) {
        log.error("Erro ao gerar PDF todos: {}", e.getMessage(), e);
        return null;
    }
}
}