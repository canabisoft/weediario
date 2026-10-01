package br.com.weediario.treatment.infrastructure.report;

import br.com.weediario.treatment.application.port.ReportRenderer;
import br.com.weediario.treatment.application.query.TreatmentQueries.DoseEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.RatingEntry;
import br.com.weediario.treatment.application.query.TreatmentQueries.SymptomView;
import br.com.weediario.treatment.application.query.TreatmentStats.DayPoint;
import br.com.weediario.treatment.application.usecase.TreatmentReportUseCase.BatchUsage;
import br.com.weediario.treatment.application.usecase.TreatmentReportUseCase.SideEffectCount;
import br.com.weediario.treatment.application.usecase.TreatmentReportUseCase.SymptomSummary;
import br.com.weediario.treatment.application.usecase.TreatmentReportUseCase.Totals;
import br.com.weediario.treatment.application.usecase.TreatmentReportUseCase.TreatmentReport;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.RatingMoment;
import br.com.weediario.treatment.domain.model.TreatmentVocabulary.SymptomKind;
import com.lowagie.text.Chunk;
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
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static br.com.weediario.treatment.infrastructure.report.ReportLabels.number;

/**
 * Relatório em PDF (OpenPDF) para levar à consulta. Mostra só o que foi registrado; o rodapé de todas as páginas
 * lembra que o documento não contém prescrição nem orientação de dose.
 */
@Component
public class PdfReportRenderer implements ReportRenderer {

    static final String DISCLAIMER = "Registro feito pelo paciente ou cuidador no weediário. Os valores em mg foram "
            + "calculados a partir das concentrações informadas nos rótulos. Este documento não contém prescrição, "
            + "orientação de dose nem avaliação clínica: a interpretação cabe ao profissional de saúde.";
    private static final int MAX_DOSE_ROWS = 400;
    private static final int MAX_CHART_SYMPTOMS = 3;

    private static final Color INK = new Color(0x1f, 0x29, 0x37);
    private static final Color MUTED = new Color(0x6b, 0x72, 0x80);
    private static final Color LINE = new Color(0xd1, 0xd5, 0xdb);
    private static final Color HEADER_BG = new Color(0xec, 0xfd, 0xf5);
    // Paleta validada (daltonismo e contraste em fundo branco): cor fixa para CBD, THC e até 3 sintomas.
    private static final Color CBD = new Color(0x00, 0x83, 0x00);
    private static final Color THC = new Color(0x4a, 0x3a, 0xa7);
    private static final Color[] SYMPTOM_COLORS = {new Color(0x2a, 0x78, 0xd6), new Color(0xeb, 0x68, 0x34),
            new Color(0x1b, 0xaf, 0x7a)};

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd/MM");

    private final Font title = font(16, Font.BOLD, INK);
    private final Font section = font(11.5f, Font.BOLD, INK);
    private final Font body = font(9, Font.NORMAL, INK);
    private final Font bold = font(9, Font.BOLD, INK);
    private final Font small = font(7.5f, Font.NORMAL, MUTED);
    private final Font cell = font(8, Font.NORMAL, INK);
    private final Font cellHeader = font(8, Font.BOLD, INK);

    @Override
    public byte[] render(TreatmentReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 54);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer());
            document.addTitle("Relatório de acompanhamento do tratamento");
            document.addCreator("weediário");
            document.open();

            header(document, report);
            summary(document, report.totals());
            products(document, report.batches());
            symptoms(document, report.symptoms());
            chart(document, writer, report);
            sideEffects(document, report.sideEffects());
            doses(document, report);

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o relatório", e);
        }
        return out.toByteArray();
    }

    // --------------------------------------------------------------------------------------- seções

    private void header(Document document, TreatmentReport report) throws DocumentException {
        document.add(new Paragraph("Relatório de acompanhamento do tratamento", title));
        Paragraph meta = new Paragraph();
        meta.setSpacingBefore(4);
        meta.add(new Chunk("Paciente: ", bold));
        meta.add(new Chunk(report.patientName(), body));
        meta.add(new Chunk("    Período: ", bold));
        meta.add(new Chunk(report.from().format(DATE) + " a " + report.to().format(DATE), body));
        meta.add(new Chunk("    Gerado em: ", bold));
        meta.add(new Chunk(report.generatedAt().atZone(report.zone()).format(DATE_TIME), body));
        document.add(meta);

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingBefore(8);
        PdfPCell notice = new PdfPCell(new Phrase(DISCLAIMER, body));
        notice.setBackgroundColor(new Color(0xff, 0xfb, 0xeb));
        notice.setBorderColor(new Color(0xfc, 0xd3, 0x4d));
        notice.setPadding(7);
        box.addCell(notice);
        document.add(box);
    }

    private void summary(Document document, Totals totals) throws DocumentException {
        heading(document, "Resumo do período");
        PdfPTable table = table(new float[]{3, 2, 3, 2});
        pair(table, "Usos registrados", String.valueOf(totals.doses()));
        pair(table, "Dias com uso", totals.daysWithDoses() + " de " + totals.days());
        pair(table, "THC total", mg(totals.thcMg()));
        pair(table, "CBD total", mg(totals.cbdMg()));
        pair(table, "THC por dia com uso (média)", mg(totals.thcMgPerDay()));
        pair(table, "CBD por dia com uso (média)", mg(totals.cbdMgPerDay()));
        document.add(table);
    }

    private void products(Document document, List<BatchUsage> batches) throws DocumentException {
        heading(document, "Produtos usados");
        if (batches.isEmpty()) {
            document.add(new Paragraph("Nenhum uso registrado no período.", body));
            return;
        }
        PdfPTable table = table(new float[]{4, 2.2f, 2.2f, 3, 1.3f, 2, 1.8f, 1.8f});
        headerRow(table, "Produto", "Forma", "Origem", "Concentração (rótulo)", "Usos", "Quantidade", "THC (mg)",
                "CBD (mg)");
        for (BatchUsage batch : batches) {
            String unit = ReportLabels.CONCENTRATION.get(batch.concentrationUnit());
            String concentration = batch.thc() == null && batch.cbd() == null ? "não informada"
                    : "THC " + number(batch.thc(), 3) + " " + unit + " · CBD " + number(batch.cbd(), 3) + " " + unit;
            row(table, batch.name(), ReportLabels.FORM.get(batch.form()),
                    batch.origin() == null ? "—" : ReportLabels.ORIGIN.get(batch.origin()), concentration,
                    String.valueOf(batch.doses()),
                    batch.stockUsed() == null ? "—" : number(batch.stockUsed(), 2) + " " + ReportLabels.STOCK_UNIT.get(batch.stockUnit()),
                    number(batch.thcMg(), 1), number(batch.cbdMg(), 1));
        }
        document.add(table);
    }

    private void symptoms(Document document, List<SymptomSummary> symptoms) throws DocumentException {
        heading(document, "Sintomas acompanhados");
        if (symptoms.isEmpty()) {
            document.add(new Paragraph("Nenhuma nota de sintoma registrada no período.", body));
            return;
        }
        PdfPTable table = table(new float[]{4, 1.6f, 2, 2, 2.4f, 2.4f, 1.8f});
        headerRow(table, "Sintoma", "Notas", "Média 1ª semana", "Média última semana", "Média antes do uso",
                "Média depois do uso", "Total");
        for (SymptomSummary symptom : symptoms) {
            String suffix = symptom.kind() == SymptomKind.SCALE ? " (0–10)" : " (contagem)";
            row(table, symptom.name() + suffix, String.valueOf(symptom.ratings()),
                    number(symptom.firstWeekAverage(), 1), number(symptom.lastWeekAverage(), 1),
                    symptom.pairs() == 0 ? "—" : number(symptom.averageBefore(), 1) + " (" + symptom.pairs() + ")",
                    symptom.pairs() == 0 ? "—" : number(symptom.averageAfter(), 1) + " (" + symptom.pairs() + ")",
                    symptom.total() == null ? "—" : String.valueOf(symptom.total()));
        }
        document.add(table);
        Paragraph note = new Paragraph("Antes/depois: só usos com as duas notas (quantidade entre parênteses).", small);
        note.setSpacingBefore(2);
        document.add(note);
    }

    private void sideEffects(Document document, List<SideEffectCount> effects) throws DocumentException {
        heading(document, "Efeitos indesejados relatados");
        if (effects.isEmpty()) {
            document.add(new Paragraph("Nenhum efeito indesejado registrado no período.", body));
            return;
        }
        PdfPTable table = table(new float[]{4, 1.5f, 1.5f, 1.5f, 1.5f});
        headerRow(table, "Efeito", "Leve", "Moderado", "Forte", "Total");
        for (SideEffectCount effect : effects) {
            row(table, ReportLabels.SIDE_EFFECT.get(effect.effect()), String.valueOf(effect.mild()),
                    String.valueOf(effect.moderate()), String.valueOf(effect.severe()), String.valueOf(effect.total()));
        }
        document.add(table);
    }

    private void doses(Document document, TreatmentReport report) throws DocumentException {
        heading(document, "Registro de usos");
        if (report.doses().isEmpty()) {
            document.add(new Paragraph("Nenhum uso registrado no período.", body));
            return;
        }
        PdfPTable table = table(new float[]{2.4f, 3.2f, 2.3f, 1.9f, 1.5f, 1.5f, 3.8f, 2.8f});
        table.setHeaderRows(1);
        headerRow(table, "Data e hora", "Produto", "Quantidade", "Via", "THC (mg)", "CBD (mg)", "Notas (antes / depois)",
                "Observações");
        List<DoseEntry> rows = report.doses().size() > MAX_DOSE_ROWS
                ? report.doses().subList(report.doses().size() - MAX_DOSE_ROWS, report.doses().size())
                : report.doses();
        for (DoseEntry dose : rows) {
            row(table, dose.takenAt().atZone(report.zone()).format(DATE_TIME), dose.batchName(),
                    number(dose.amount(), 2) + " " + ReportLabels.DOSE_UNIT.get(dose.unit()),
                    ReportLabels.ROUTE.get(dose.route()), number(dose.thcMg(), 1), number(dose.cbdMg(), 1),
                    ratings(dose), dose.notes() == null ? "" : dose.notes());
        }
        document.add(table);
        if (rows.size() < report.doses().size()) {
            document.add(new Paragraph("Mostrando os " + MAX_DOSE_ROWS + " usos mais recentes de "
                    + report.doses().size() + ".", small));
        }
    }

    /** "Dor: 7 / 3; Ansiedade: 6 / —" (antes / depois). */
    private static String ratings(DoseEntry dose) {
        Map<UUID, List<RatingEntry>> bySymptom = dose.ratings().stream()
                .collect(Collectors.groupingBy(RatingEntry::symptomId, java.util.LinkedHashMap::new, Collectors.toList()));
        List<String> parts = new ArrayList<>();
        bySymptom.values().forEach(list -> {
            String before = list.stream().filter(r -> r.moment() == RatingMoment.BEFORE).map(r -> String.valueOf(r.value()))
                    .findFirst().orElse("—");
            String after = list.stream().filter(r -> r.moment() == RatingMoment.AFTER).map(r -> String.valueOf(r.value()))
                    .findFirst().orElse("—");
            parts.add(list.get(0).symptom() + ": " + before + " / " + after);
        });
        return String.join("; ", parts);
    }

    // --------------------------------------------------------------------------------------- gráfico

    /**
     * Dois gráficos alinhados pelo mesmo eixo de datas (nunca dois eixos Y no mesmo gráfico): mg/dia em barras
     * empilhadas (CBD e THC) e, embaixo, a média diária de cada sintoma de escala (0–10) em linhas.
     */
    private void chart(Document document, PdfWriter writer, TreatmentReport report) throws DocumentException {
        heading(document, "Uso diário e notas dos sintomas");
        List<DayPoint> days = report.days();
        List<SymptomView> scales = report.symptomList().stream()
                .filter(s -> s.kind() == SymptomKind.SCALE)
                .filter(s -> days.stream().anyMatch(d -> d.symptoms().containsKey(s.id())))
                .limit(MAX_CHART_SYMPTOMS)
                .toList();
        PdfContentByte canvas = writer.getDirectContent();
        BaseFont font = body.getCalculatedBaseFont(false);

        double axisMax = niceCeiling(days.stream().mapToDouble(d -> value(d.thcMg()) + value(d.cbdMg())).max().orElse(0));
        Plot mg = new Plot(canvas.createTemplate(CHART_WIDTH, 150), days.size());
        mg.grid(font, axisMax, 0, "mg por dia");
        for (int i = 0; i < days.size(); i++) {
            DayPoint day = days.get(i);
            float x = mg.slotStart(i) + (mg.slot() - mg.barWidth()) / 2;
            float cbd = (float) (value(day.cbdMg()) / axisMax * mg.plotHeight());
            float thc = (float) (value(day.thcMg()) / axisMax * mg.plotHeight());
            bar(mg.template(), x, mg.bottom(), mg.barWidth(), cbd, CBD);
            // Respiro entre os segmentos empilhados, para separar sem depender só da cor.
            float gap = cbd > 0 && thc > 0 ? 1 : 0;
            bar(mg.template(), x, mg.bottom() + cbd + gap, mg.barWidth(), Math.max(thc - gap, 0), THC);
        }
        mg.dayLabels(font, days);
        float legendX = legendEntry(mg.template(), font, LEFT, mg.height() - 9, CBD, false, "CBD (mg)");
        legendEntry(mg.template(), font, legendX, mg.height() - 9, THC, false, "THC (mg)");
        add(document, mg.template());

        if (scales.isEmpty()) {
            return;
        }
        Plot notes = new Plot(canvas.createTemplate(CHART_WIDTH, 130), days.size());
        notes.grid(font, 10, 1, "nota média do dia (0–10)");
        float legend = LEFT;
        for (int s = 0; s < scales.size(); s++) {
            UUID symptom = scales.get(s).id();
            PdfTemplate t = notes.template();
            t.setColorStroke(SYMPTOM_COLORS[s]);
            t.setColorFill(SYMPTOM_COLORS[s]);
            t.setLineWidth(1.5f);
            // Tracejado diferente por sintoma: continua legível impresso em preto e branco.
            t.setLineDash(DASHES[s], 0);
            boolean drawing = false;
            for (int i = 0; i < days.size(); i++) {
                BigDecimal average = days.get(i).symptoms().get(symptom);
                if (average == null) {
                    continue; // dia sem nota não vira zero: a linha liga os dias com registro
                }
                float x = notes.center(i);
                float y = notes.bottom() + (float) (average.doubleValue() / 10 * notes.plotHeight());
                if (drawing) {
                    t.lineTo(x, y);
                } else {
                    t.moveTo(x, y);
                    drawing = true;
                }
            }
            t.stroke();
            t.setLineDash(0);
            for (int i = 0; i < days.size(); i++) {
                BigDecimal average = days.get(i).symptoms().get(symptom);
                if (average != null) {
                    t.circle(notes.center(i), notes.bottom() + (float) (average.doubleValue() / 10 * notes.plotHeight()), 2f);
                    t.fill();
                }
            }
            legend = legendEntry(t, font, legend, notes.height() - 9, SYMPTOM_COLORS[s], true, scales.get(s).name());
        }
        notes.dayLabels(font, days);
        add(document, notes.template());
    }

    private static final float CHART_WIDTH = PageSize.A4.getWidth() - 72;
    private static final float LEFT = 34;
    private static final float[][] DASHES = {{1, 0}, {4, 2}, {1.5f, 1.5f}};

    private static void add(Document document, PdfTemplate template) throws DocumentException {
        Image image = Image.getInstance(template);
        image.setSpacingBefore(4);
        document.add(image);
    }

    /** Área de um gráfico com as mesmas margens e colunas de dias do outro, para os dois alinharem. */
    private record Plot(PdfTemplate template, int days) {

        static final float RIGHT = 10;
        static final float BOTTOM = 24;
        static final float TOP = 22;

        float height() {
            return template.getHeight();
        }

        float bottom() {
            return BOTTOM;
        }

        float plotHeight() {
            return height() - BOTTOM - TOP;
        }

        float slot() {
            return (CHART_WIDTH - LEFT - RIGHT) / Math.max(days, 1);
        }

        float slotStart(int i) {
            return LEFT + slot() * i;
        }

        float center(int i) {
            return slotStart(i) + slot() / 2;
        }

        float barWidth() {
            return Math.max(Math.min(slot() * 0.7f, 14), 1);
        }

        void grid(BaseFont font, double max, int decimals, String label) {
            template.setLineWidth(0.4f);
            template.setColorStroke(LINE);
            for (int i = 0; i <= 4; i++) {
                float y = BOTTOM + plotHeight() * i / 4;
                template.moveTo(LEFT, y);
                template.lineTo(CHART_WIDTH - RIGHT, y);
                template.stroke();
                text(template, font, number(BigDecimal.valueOf(max * i / 4), decimals), LEFT - 4, y - 2.5f,
                        PdfContentByte.ALIGN_RIGHT, MUTED);
            }
            text(template, font, label, CHART_WIDTH - RIGHT, height() - 9, PdfContentByte.ALIGN_RIGHT, MUTED);
        }

        void dayLabels(BaseFont font, List<DayPoint> points) {
            int every = Math.max(1, (int) Math.ceil(points.size() / 10.0));
            for (int i = 0; i < points.size(); i += every) {
                text(template, font, points.get(i).day().format(SHORT_DAY), center(i), BOTTOM - 10,
                        PdfContentByte.ALIGN_CENTER, MUTED);
            }
        }
    }

    private static float legendEntry(PdfTemplate template, BaseFont font, float x, float y, Color color, boolean line,
                                     String label) {
        template.setColorFill(color);
        if (line) {
            template.circle(x + 3, y + 2, 2.2f);
        } else {
            template.rectangle(x, y - 1, 6, 6);
        }
        template.fill();
        text(template, font, label, x + 9, y, PdfContentByte.ALIGN_LEFT, INK);
        return x + 9 + font.getWidthPoint(label, 6.5f) + 14;
    }

    private static void bar(PdfTemplate template, float x, float y, float width, float height, Color color) {
        if (height <= 0) {
            return;
        }
        template.setColorFill(color);
        template.rectangle(x, y, width, height);
        template.fill();
    }

    private static void text(PdfTemplate template, BaseFont font, String text, float x, float y, int align, Color color) {
        template.beginText();
        template.setFontAndSize(font, 6.5f);
        template.setColorFill(color);
        template.showTextAligned(align, text, x, y, 0);
        template.endText();
    }

    private static double value(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    /** Arredonda o topo do eixo para 1, 2 ou 5 × 10^n. */
    static double niceCeiling(double max) {
        if (max <= 0) {
            return 10;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(max)));
        for (double step : new double[]{1, 2, 5, 10}) {
            if (step * magnitude >= max) {
                return step * magnitude;
            }
        }
        return 10 * magnitude;
    }

    // ------------------------------------------------------------------------------------ auxiliares

    private void heading(Document document, String text) throws DocumentException {
        Paragraph heading = new Paragraph(text, section);
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(5);
        document.add(heading);
    }

    private PdfPTable table(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        return table;
    }

    private void headerRow(PdfPTable table, String... labels) {
        for (String label : labels) {
            PdfPCell header = new PdfPCell(new Phrase(label, cellHeader));
            header.setBackgroundColor(HEADER_BG);
            style(header);
            table.addCell(header);
        }
    }

    private void row(PdfPTable table, String... values) {
        for (String value : values) {
            PdfPCell data = new PdfPCell(new Phrase(value, cell));
            style(data);
            table.addCell(data);
        }
    }

    private void pair(PdfPTable table, String label, String value) {
        PdfPCell name = new PdfPCell(new Phrase(label, cell));
        style(name);
        name.setBackgroundColor(HEADER_BG);
        table.addCell(name);
        PdfPCell data = new PdfPCell(new Phrase(value, bold));
        style(data);
        table.addCell(data);
    }

    private static void style(PdfPCell cell) {
        cell.setBorderColor(LINE);
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    }

    private static String mg(BigDecimal value) {
        return value == null ? "—" : number(value, 1) + " mg";
    }

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED, size, style, color);
    }

    /** Número da página e o lembrete de que o documento é só registro, em todas as páginas. */
    private class Footer extends PdfPageEventHelper {

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            ColumnText column = new ColumnText(writer.getDirectContent());
            column.setSimpleColumn(document.left(), 14, document.right() - 40, 44);
            column.addElement(new Paragraph("Documento de registro do paciente. Não contém prescrição nem orientação de "
                    + "dose.", small));
            try {
                column.go();
            } catch (DocumentException e) {
                throw new IllegalStateException(e);
            }
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), small), page.getRight() - 36, 22, 0);
        }
    }
}
