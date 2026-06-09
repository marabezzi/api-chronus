package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.config.AfdtEmpresaConfig;
import br.com.atom.api_chronus.config.LogoConfig;
import br.com.atom.api_chronus.dto.EspelhoDiaDTO;
import br.com.atom.api_chronus.dto.EspelhoMarcacaoDTO;
import br.com.atom.api_chronus.dto.EspelhoResponseDTO;
import br.com.atom.api_chronus.dto.EspelhoSemanaDTO;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
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
import java.util.List;

/**
 * Gera o PDF do Espelho de Ponto conforme Anexo II da Portaria 1510/MTE.
 *
 * Seções:
 *   1. Cabeçalho: logo empresa + dados empregador/empregado
 *   2. Horários contratuais (vazia — não gerenciado pelo REP)
 *   3. Período de apuração
 *   4. Tabela principal: Dia | Marcações | Jornada | Total
 *   5. Totalizadores do período
 *   6. Resumo semanal
 *   7. Assinaturas
 *   Rodapé: logo Chronus + número de página + data/hora (timezone correto)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EspelhoPdfService {

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
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRANCO);
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
    private static final Font F_ENTRADA =
            FontFactory.getFont(FontFactory.HELVETICA, 7, VERDE);
    private static final Font F_SAIDA =
            FontFactory.getFont(FontFactory.HELVETICA, 7, AZUL_MEDIO);
    private static final Font F_INCOMPLETO =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, VERMELHO);
    private static final Font F_OK =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, VERDE);
    private static final Font F_TOTAL =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRANCO);
    private static final Font F_TOTAL_VAL =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRANCO);
    private static final Font F_RODAPE =
            FontFactory.getFont(FontFactory.HELVETICA, 6, Color.GRAY);
    private static final Font F_SEC_TITULO =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRANCO);

    // ─────────────────────────────────────────────────────────────────────
    // MÉTODO PRINCIPAL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gera o PDF do espelho conforme Portaria 1510/MTE Anexo II.
     */
    public byte[] gerar(EspelhoResponseDTO espelho) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 28, 28, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            // Carrega logos — null se arquivo não existir (degrada graciosamente)
            Image logoEmpresa = carregarImagem(logoConfig.getEmpresaPath(), 80, 40);
            Image logoChronus = carregarImagem(logoConfig.getChronusPath(), 60, 25);

            writer.setPageEvent(new RodapePagina(espelho, logoChronus, timezone));
            doc.open();

            // 1. Cabeçalho
            doc.add(gerarCabecalho(espelho, logoEmpresa));
            doc.add(new Paragraph(" "));

            // 2. Horários contratuais
            doc.add(gerarHorariosContratuais());
            doc.add(new Paragraph(" "));

            // 3. Período
            doc.add(gerarPeriodo(espelho));
            doc.add(new Paragraph(" "));

            // 4. Tabela principal
            doc.add(gerarTabelaPrincipal(espelho));
            doc.add(new Paragraph(" "));

            // 5. Totalizadores
            doc.add(gerarTotalizadores(espelho));

            // 6. Resumo semanal
            if (espelho.getSemanas() != null && !espelho.getSemanas().isEmpty()) {
                doc.add(new Paragraph(" "));
                doc.add(gerarResumoSemanal(espelho));
            }

            // 7. Assinaturas
            doc.add(new Paragraph(" "));
            doc.add(gerarAssinaturas());

            doc.close();
            log.info("PDF espelho gerado: {} | {} a {}",
                    espelho.getNome(),
                    espelho.getDataInicial(),
                    espelho.getDataFinal());
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Erro ao gerar PDF: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEÇÕES DO PDF
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 1. Cabeçalho com logo da empresa à esquerda e dados à direita.
     *
     * Layout:
     *   ┌──────────┬────────────────────────────────┐
     *   │   LOGO   │  RELATORIO ESPELHO DE PONTO    │
     *   │ EMPRESA  ├────────────────────────────────┤
     *   │          │  dados empregador | empregado  │
     *   └──────────┴────────────────────────────────┘
     */
    private PdfPTable gerarCabecalho(EspelhoResponseDTO espelho, Image logoEmpresa)
            throws DocumentException {

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.5f, 6.5f});

        // ── Célula do logo da empresa ─────────────────────────────────────
        PdfPCell cLogo;
        if (logoEmpresa != null) {
            cLogo = new PdfPCell(logoEmpresa, true);
        } else {
            // Placeholder se o logo não existir
            cLogo = celula("LOGO", F_LABEL, Element.ALIGN_CENTER);
        }
        cLogo.setBackgroundColor(AZUL_CLARO);
        cLogo.setHorizontalAlignment(Element.ALIGN_CENTER);
        cLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cLogo.setPadding(8);
        cLogo.setRowspan(2); // Ocupa título + dados
        t.addCell(cLogo);

        // ── Barra de título ───────────────────────────────────────────────
        PdfPCell titulo = celula(
                "RELATORIO ESPELHO DE PONTO ELETRONICO",
                F_TITULO, Element.ALIGN_CENTER);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(7);
        titulo.setBorder(0);
        t.addCell(titulo);

        // ── Dados da empresa e funcionário ────────────────────────────────
        PdfPTable dados = new PdfPTable(4);
        dados.setWidthPercentage(100);
        dados.setWidths(new float[]{1.2f, 3f, 1.2f, 3f});

        addDado(dados, "Empregador:", empresaConfig.getRazaoSocial());
        addDado(dados, "CNPJ:",       formatCnpj(empresaConfig.getCnpj()));
        addDado(dados, "Endereco:",   nvl(empresaConfig.getLocal()));
        addDado(dados, "Emitido em:", agora(timezone));
        addDado(dados, "Empregado:",  espelho.getNome());
        addDado(dados, "PIS:",        espelho.getPis());
        addDado(dados, "Admissao:",   "-");
        addDado(dados, "Matricula:",  "-");

        PdfPCell cDados = new PdfPCell(dados);
        cDados.setBackgroundColor(AZUL_CLARO);
        cDados.setPadding(5);
        cDados.setBorderColor(AZUL_ESCURO);
        cDados.setBorderWidth(1f);
        t.addCell(cDados);

        return t;
    }

    /**
     * 2. Tabela de horários contratuais (vazia — não gerenciado pelo REP).
     */
    private PdfPTable gerarHorariosContratuais() throws DocumentException {
        PdfPTable t = new PdfPTable(5);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{2f, 1.5f, 1.5f, 1.5f, 1.5f});

        PdfPCell titulo = new PdfPCell(
                new Phrase("HORARIOS CONTRATUAIS DO EMPREGADO", F_SEC_TITULO));
        titulo.setColspan(5);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(5);
        titulo.setBorderColor(BRANCO);
        t.addCell(titulo);

        for (String cab : new String[]{
                "Cod. Horario (CH)", "Entrada", "Saida", "Entrada", "Saida"}) {
            PdfPCell c = celula(cab, F_CAB_TAB, Element.ALIGN_CENTER);
            c.setBackgroundColor(AZUL_MEDIO);
            c.setPadding(4);
            t.addCell(c);
        }

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 5; j++) {
                PdfPCell c = celula("-", F_CELULA, Element.ALIGN_CENTER);
                c.setBackgroundColor(i % 2 == 0 ? BRANCO : CINZA_CLARO);
                c.setPadding(4);
                t.addCell(c);
            }
        }

        return t;
    }

    /**
     * 3. Período de apuração.
     */
    private PdfPTable gerarPeriodo(EspelhoResponseDTO espelho)
            throws DocumentException {

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1f, 5f});

        PdfPCell label = celula("PERIODO:", F_LABEL, Element.ALIGN_LEFT);
        label.setBackgroundColor(AZUL_CLARO);
        label.setPadding(5);
        t.addCell(label);

        PdfPCell val = celula(
                "De " + formatData(espelho.getDataInicial())
                + " ate " + formatData(espelho.getDataFinal()),
                F_VALOR, Element.ALIGN_LEFT);
        val.setBackgroundColor(AZUL_CLARO);
        val.setPadding(5);
        t.addCell(val);

        return t;
    }

    /**
     * 4. Tabela principal de marcações e jornada realizada.
     *
     * Coluna de marcações exibe todas as batidas do dia em uma célula:
     * "08:27  12:01  13:00  17:29"
     */
    private PdfPTable gerarTabelaPrincipal(EspelhoResponseDTO espelho)
            throws DocumentException {

        PdfPTable t = new PdfPTable(7);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{
                1.0f,   // Dia
                3.0f,   // Marcações (todas na mesma célula)
                1.0f,   // Jornada Ent.1
                1.0f,   // Jornada Sai.1
                1.0f,   // Jornada Ent.2
                1.0f,   // Jornada Sai.2
                1.2f    // Total trabalhado
        });

        addCabGrupo(t, "DIA",                            1, AZUL_ESCURO);
        addCabGrupo(t, "MARCACOES REGISTRADAS NO PONTO", 1, AZUL_MEDIO);
        addCabGrupo(t, "JORNADA REALIZADA",              4, AZUL_ESCURO);
        addCabGrupo(t, "TOTAL",                          1, AZUL_MEDIO);

        addSubCab(t, "dd/MM");
        addSubCab(t, "hh:mm  hh:mm  hh:mm  hh:mm");
        addSubCab(t, "Ent.1");
        addSubCab(t, "Sai.1");
        addSubCab(t, "Ent.2");
        addSubCab(t, "Sai.2");
        addSubCab(t, "HH:mm");

        boolean alt = false;
        for (EspelhoDiaDTO dia : espelho.getDias()) {
            Color bg = alt ? CINZA_CLARO : BRANCO;
            List<EspelhoMarcacaoDTO> ms = dia.getMarcacoes();

            Font fDia = dia.isTemInconsistencia() ? F_INCOMPLETO : F_CELULA_BOLD;
            addCelulaLinha(t, formatDia(dia.getData()),
                    fDia, Element.ALIGN_CENTER, bg);

            // Todas as marcações na mesma célula
            StringBuilder marcStr = new StringBuilder();
            for (int i = 0; i < ms.size(); i++) {
                if (i > 0) marcStr.append("  ");
                marcStr.append(ms.get(i).getHorario());
            }
            addCelulaLinha(t, marcStr.toString(),
                    F_CELULA, Element.ALIGN_CENTER, bg);

            // Jornada E/S
            String[] jornada = new String[8];
            int ej = 0, sj = 0;
            for (EspelhoMarcacaoDTO m : ms) {
                if (m.getTipo().startsWith("Entrada") && ej < 4) {
                    jornada[ej * 2] = m.getHorario(); ej++;
                } else if (m.getTipo().startsWith("Sa") && sj < 4) {
                    jornada[sj * 2 + 1] = m.getHorario(); sj++;
                }
            }
            for (int i = 0; i < 4; i++) {
                String val = jornada[i] != null ? jornada[i] : "";
                Font f = (i % 2 == 0) ? F_ENTRADA : F_SAIDA;
                addCelulaLinha(t, val, f, Element.ALIGN_CENTER, bg);
            }

            Font fTot = dia.isTemInconsistencia() ? F_INCOMPLETO : F_OK;
            String totalStr = dia.getTotalTrabalhado()
                    + (dia.isTemInconsistencia() ? " !" : "");
            addCelulaLinha(t, totalStr, fTot, Element.ALIGN_CENTER, bg);

            alt = !alt;
        }

        return t;
    }

    /**
     * 5. Totalizadores do período.
     */
    @SuppressWarnings("null")
    private PdfPTable gerarTotalizadores(EspelhoResponseDTO espelho)
            throws DocumentException {

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);

        long inc = espelho.getDias().stream()
                .filter(EspelhoDiaDTO::isTemInconsistencia).count();

        addTotalBox(t, "DIAS COM PONTO",
                String.valueOf(espelho.getTotalDiasComPonto()), AZUL_ESCURO);
        addTotalBox(t, "TOTAL DE HORAS",
                espelho.getTotalHorasTrabalhadas(), VERDE);
        addTotalBox(t, "DIAS INCOMPLETOS",
                String.valueOf(inc),
                inc > 0 ? VERMELHO : new Color(80, 80, 80));
        addTotalBox(t, "MEDIA DIARIA",
                calcularMedia(espelho), AZUL_MEDIO);

        return t;
    }

    /**
     * 6. Resumo semanal: Semana | Dias | Total Horas | Média Diária
     */
    private PdfPTable gerarResumoSemanal(EspelhoResponseDTO espelho)
            throws DocumentException {

        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{3.5f, 1.5f, 1.5f, 1.5f});

        PdfPCell titulo = new PdfPCell(
                new Phrase("RESUMO SEMANAL", F_SEC_TITULO));
        titulo.setColspan(4);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(5);
        titulo.setBorderColor(BRANCO);
        t.addCell(titulo);

        for (String cab : new String[]{
                "SEMANA", "DIAS TRABALHADOS", "TOTAL HORAS", "MEDIA DIARIA"}) {
            PdfPCell c = celula(cab, F_CAB_TAB_DARK, Element.ALIGN_CENTER);
            c.setBackgroundColor(AZUL_CLARO);
            c.setPadding(4);
            c.setBorderColor(CINZA_MEDIO);
            t.addCell(c);
        }

        boolean alt = false;
        for (EspelhoSemanaDTO semana : espelho.getSemanas()) {
            Color bg = alt ? CINZA_CLARO : BRANCO;
            addCelulaLinha(t, semana.getSemana(),
                    F_CELULA_BOLD, Element.ALIGN_LEFT, bg);
            addCelulaLinha(t, String.valueOf(semana.getDiasTrabalhados()),
                    F_CELULA, Element.ALIGN_CENTER, bg);
            addCelulaLinha(t, semana.getTotalHoras(),
                    F_CELULA_BOLD, Element.ALIGN_CENTER, bg);
            addCelulaLinha(t, semana.getMediaDiaria(),
                    F_CELULA, Element.ALIGN_CENTER, bg);
            alt = !alt;
        }

        return t;
    }

    /**
     * 7. Bloco de assinaturas: funcionário e responsável pelo setor.
     */
    private PdfPTable gerarAssinaturas() throws DocumentException {

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1f, 1f});

        PdfPCell titulo = new PdfPCell(
                new Phrase("ASSINATURAS", F_SEC_TITULO));
        titulo.setColspan(2);
        titulo.setBackgroundColor(AZUL_ESCURO);
        titulo.setPadding(5);
        titulo.setBorderColor(BRANCO);
        t.addCell(titulo);

        t.addCell(celulaAssinatura("Assinatura do Funcionario:"));
        t.addCell(celulaAssinatura("Assinatura do Responsavel pelo Setor:"));

        return t;
    }

    /**
     * Célula de assinatura com linha para assinar.
     */
    private PdfPCell celulaAssinatura(String label) {
        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);

        PdfPCell cLabel = celula(label, F_LABEL, Element.ALIGN_LEFT);
        cLabel.setBorder(Rectangle.NO_BORDER);
        cLabel.setPaddingTop(10);
        cLabel.setPaddingLeft(15);
        cLabel.setPaddingBottom(4);
        inner.addCell(cLabel);

        PdfPCell cLinha = celula("", F_CELULA, Element.ALIGN_LEFT);
        cLinha.setBorder(Rectangle.BOTTOM);
        cLinha.setBorderColor(PRETO);
        cLinha.setBorderWidth(0.5f);
        cLinha.setMinimumHeight(35f);
        cLinha.setPaddingLeft(15f);
        cLinha.setPaddingRight(15f);
        inner.addCell(cLinha);

        PdfPCell cEspaco = celula("", F_CELULA, Element.ALIGN_LEFT);
        cEspaco.setBorder(Rectangle.NO_BORDER);
        cEspaco.setMinimumHeight(10f);
        inner.addCell(cEspaco);

        PdfPCell cell = new PdfPCell(inner);
        cell.setPadding(5);
        cell.setBorderColor(CINZA_MEDIO);
        return cell;
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

    private void addCabGrupo(PdfPTable t, String texto,
                              int colspan, Color bg) {
        PdfPCell c = celula(texto, F_CAB_TAB, Element.ALIGN_CENTER);
        c.setColspan(colspan);
        c.setBackgroundColor(bg);
        c.setPadding(4);
        c.setBorderColor(BRANCO);
        t.addCell(c);
    }

    private void addSubCab(PdfPTable t, String texto) {
        PdfPCell c = celula(texto, F_CAB_TAB_DARK, Element.ALIGN_CENTER);
        c.setBackgroundColor(CINZA_CLARO);
        c.setPadding(3);
        c.setBorderColor(CINZA_MEDIO);
        t.addCell(c);
    }

    private void addCelulaLinha(PdfPTable t, String texto,
                                 Font font, int align, Color bg) {
        PdfPCell c = celula(texto, font, align);
        c.setBackgroundColor(bg);
        c.setPadding(3);
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

    /**
     * Carrega uma imagem PNG do disco e redimensiona.
     * Retorna null se o arquivo não existir (PDF gerado sem o logo).
     */
    private Image carregarImagem(String caminho, float largura, float altura) {
        try {
            File f = new File(caminho);
            if (!f.exists()) {
                log.warn("Logo não encontrado: {} — PDF gerado sem o logo.", caminho);
                return null;
            }
            Image img = Image.getInstance(caminho);
            img.scaleToFit(largura, altura);
            return img;
        } catch (Exception e) {
            log.warn("Erro ao carregar logo {}: {}", caminho, e.getMessage());
            return null;
        }
    }

    // ── Formatadores ──────────────────────────────────────────────────────

    /** Data/hora atual no timezone configurado */
    private String agora(String tz) {
        return ZonedDateTime.now(ZoneId.of(tz))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String formatData(String iso) {
        if (iso == null || iso.length() < 10) return iso;
        return iso.substring(8, 10) + "/" + iso.substring(5, 7)
                + "/" + iso.substring(0, 4);
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

    private String calcularMedia(EspelhoResponseDTO e) {
        if (e.getTotalDiasComPonto() == 0) return "00:00";
        String[] p = e.getTotalHorasTrabalhadas().split(":");
        int totalMin = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        int media    = totalMin / e.getTotalDiasComPonto();
        return String.format("%02d:%02d", media / 60, media % 60);
    }

    // ─────────────────────────────────────────────────────────────────────
    // RODAPÉ COM LOGO CHRONUS + NÚMERO DE PÁGINA
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Rodapé de cada página com:
     *   - Logo do Chronus à esquerda
     *   - Texto central: nome, data/hora (timezone correto) e página
     */
    private static class RodapePagina extends PdfPageEventHelper {

        private final EspelhoResponseDTO espelho;
        private final Image              logoChronus;
        private final String             timezone;

        RodapePagina(EspelhoResponseDTO e, Image logo, String tz) {
            this.espelho     = e;
            this.logoChronus = logo;
            this.timezone    = tz;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            try {
                PdfContentByte cb = writer.getDirectContent();

                // Logo Chronus no rodapé à esquerda
                if (logoChronus != null) {
                    float x = doc.left();
                    float y = doc.bottom() - 20;
                    logoChronus.setAbsolutePosition(x, y);
                    cb.addImage(logoChronus);
                }

                // Texto central do rodapé com timezone correto
                String agora = ZonedDateTime.now(ZoneId.of(timezone))
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

                        String txt = "Chronus  |  " + espelho.getNome()
                        + "  |  Emitido em: " + agora
                        + "  |  Pag. " + writer.getPageNumber();
                
                Phrase p = new Phrase(txt, EspelhoPdfService.F_RODAPE);  // ← usa campo estático
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, p,
                        (doc.left() + doc.right()) / 2,
                        doc.bottom() - 15, 0);

            } catch (Exception ignored) {}
        }
    }
}
