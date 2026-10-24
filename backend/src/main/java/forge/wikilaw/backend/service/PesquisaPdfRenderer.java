package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.PesquisaPdfRequest;
import java.awt.Color;
import java.io.*;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PesquisaPdfRenderer {
    private static final Color INK = new Color(31, 47, 61);
    private static final Color MUTED = new Color(91, 105, 117);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] render(PesquisaPdfRequest request, List<DocumentQueryService.Summary> results,
            OffsetDateTime generatedAt) throws IOException {
        try (PDDocument pdf = new PDDocument(IOUtils.createTempFileOnlyStreamCache());
                var regularResource = fontResource("NotoSans-Regular.ttf");
                var boldResource = fontResource("NotoSans-Bold.ttf")) {
            var regular = PDType0Font.load(pdf, regularResource);
            var bold = PDType0Font.load(pdf, boldResource);
            pdf.getDocumentInformation().setTitle("WikiLaw - Resultados da pesquisa");
            pdf.getDocumentInformation().setAuthor("WikiLaw");
            try (var page = new Report(pdf, regular, bold)) {
                page.heading("Relatório de pesquisa", 22);
                page.paragraph("Gerado em " + generatedAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm xxx")), 9, MUTED);
                page.heading("Dados da pesquisa", 14);
                page.field("Descrição do caso", blank(request.termo()) ? "Pesquisa inicial, sem termo informado" : request.termo());
                page.field("Tipos de documento", String.join(", ", request.categorias().stream().map(PesquisaPdfRenderer::categoryName).toList()));
                page.field("Fonte", blank(request.fonte()) ? "Todas as fontes" : request.fonte());
                page.field("Tribunal", blank(request.tribunal()) ? "Todos os tribunais" : request.tribunal());
                page.field("Período", (request.dataDe() == null ? "Sem início definido" : DATE.format(request.dataDe()))
                        + " até " + (request.dataAte() == null ? "sem fim definido" : DATE.format(request.dataAte())));
                page.field("Ordenação", switch (request.ordenacao()) {
                    case "mais-antigos" -> "Mais antigos primeiro";
                    case "mais-similares" -> "Mais similares ao caso primeiro";
                    default -> "Mais recentes primeiro";
                });                page.field("Resultados exportados", String.valueOf(results.size()));
                var range = request.intervaloPaginas();
                if (range != null) {
                    page.field("Páginas da pesquisa", range.de() + " a " + range.ate()
                            + " (" + range.itensPorPagina() + " documentos por página)");
                    page.field("Total de resultados da pesquisa", String.valueOf(range.totalResultados()));
                }
                page.paragraph("Este relatório contém os resultados selecionados da pesquisa. A numeração das páginas deste PDF é independente das páginas da pesquisa. A base pode receber novos documentos após a exportação.", 9, MUTED);
                page.heading("Resultados encontrados", 16);
                int index = range == null ? 0 : (range.de() - 1) * range.itensPorPagina();
                for (var result : results) {
                    page.resultHeading(++index, result.titulo(), result.categoria());
                    page.field("Fonte", result.fonte());
                    page.field("Tipo de documento", result.tipoDocumento());
                    page.field("Processo / tema", result.numeroProcessoOuTema());
                    page.field("Tribunal", result.tribunal());
                    page.field("Órgão julgador", result.orgaoJulgador());
                    page.field("Autores / relator", result.autoresOuRelator());
                    page.field("Julgamento", date(result.dataJulgamento()));
                    page.field("Publicação", date(result.dataPublicacao()));
                    page.field("Data original", result.dataOriginal());
                    page.field("Situação", result.situacao());
                    page.field("Resumo / ementa / questão jurídica", blank(result.resumoOuEmenta()) ? "Não informado pela fonte." : result.resumoOuEmenta());
                    page.field("precedentes".equals(result.categoria()) ? "Tese firmada" : "Decisão", result.decisao());
                    page.field("Periódico", result.periodico());
                    page.field("DOI", result.doi());
                    page.field("ISSN", result.issn());
                    page.field("Idioma", result.idioma());
                    page.field("Palavras-chave", result.palavrasChave());
                    page.sourceLink(result.urlOriginal());
                    page.gap(14);
                }
            }
            addFooters(pdf, regular);
            var output = new ByteArrayOutputStream();
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private InputStream fontResource(String name) throws IOException {
        var resource = getClass().getResourceAsStream("/fonts/" + name);
        if (resource == null) throw new IOException("PDF font resource missing: " + name);
        return resource;
    }

    static String categoryName(String category) {
        return switch (category) {
            case "decisoes" -> "Jurisprudência";
            case "precedentes" -> "Precedente";
            case "doutrina" -> "Doutrina";
            default -> category;
        };
    }

    private static boolean blank(String text) { return text == null || text.isBlank(); }
    private static String date(LocalDate date) { return date == null ? null : DATE.format(date); }

    private static void addFooters(PDDocument pdf, PDFont font) throws IOException {
        int number = 0;
        for (var page : pdf.getPages()) {
            try (var stream = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.setNonStrokingColor(MUTED);
                stream.beginText();
                stream.setFont(font, 9);
                stream.newLineAtOffset(52, 30);
                stream.showText("WikiLaw | Página " + (++number) + " de " + pdf.getNumberOfPages());
                stream.endText();
            }
        }
    }

    private static final class Report implements AutoCloseable {
        private static final float MARGIN = 52;
        private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
        private final PDDocument document;
        private final PDType0Font regular;
        private final PDType0Font bold;
        private final Map<Integer,String> glyphs = new HashMap<>();
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        Report(PDDocument document, PDType0Font regular, PDType0Font bold) throws IOException {
            this.document = document; this.regular = regular; this.bold = bold;
            nextPage();
        }

        private void nextPage() throws IOException {
            if (stream != null) stream.close();
            if (document.getNumberOfPages() >= 1000) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "O relatório excede 1.000 páginas. Refine os filtros e tente novamente.");
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
            line("WIKILAW  /  PESQUISA JURÍDICA", bold, 10, MUTED, null);
            stream.setStrokingColor(new Color(213, 221, 227));
            stream.moveTo(MARGIN, y); stream.lineTo(MARGIN + WIDTH, y); stream.stroke();
            y -= 24;
        }

        private void ensure(float height) throws IOException { if (y - height < 56) nextPage(); }
        void gap(float height) { y -= height; }

        void heading(String text, float size) throws IOException {
            ensure(size * 2 + 16);
            gap(8);
            write(text, bold, size, INK, null);
            gap(8);
        }

        void resultHeading(int index, String title, String category) throws IOException {
            ensure(90);
            write(index + ". " + categoryName(category), bold, 9, MUTED, null);
            write(blank(title) ? "Documento sem título" : title, bold, 13, INK, null);
            gap(8);
        }

        void field(String label, String value) throws IOException {
            if (blank(value)) return;
            ensure(36);
            write(label, bold, 9, MUTED, null);
            write(value, regular, 10, INK, null);
            gap(5);
        }

        void paragraph(String text, float size, Color color) throws IOException {
            write(text, regular, size, color, null);
            gap(8);
        }

        void sourceLink(String value) throws IOException {
            String url = null;
            try {
                var uri = URI.create(value == null ? "" : value.trim());
                if (uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) url = uri.toString();
            } catch (IllegalArgumentException ignored) { /* An unsafe/invalid original URL is not a PDF action. */ }
            if (url == null) { field("Fonte original", "Link indisponível."); return; }
            ensure(36);
            write("Fonte original", bold, 9, MUTED, null);
            write(url, regular, 9, new Color(36, 91, 120), url);
        }

        /** Unicode unsupported by the bundled font remains visible as a U+ notation. */
        private String normalize(String value) throws IOException {
            var result = new StringBuilder();
            for (int cp : value.codePoints().toArray()) {
                if (cp == '\r') continue;
                if (cp == '\n') { result.append('\n'); continue; }
                if (Character.isWhitespace(cp) || Character.isISOControl(cp)) { result.append(' '); continue; }
                String glyph = glyphs.get(cp);
                if (glyph == null) {
                    glyph = regular.hasGlyph(cp) && bold.hasGlyph(cp)
                            ? new String(Character.toChars(cp)) : "[U+" + Integer.toHexString(cp).toUpperCase(Locale.ROOT) + "]";
                    glyphs.put(cp, glyph);
                }
                result.append(glyph);
            }
            return result.toString();
        }

        private void write(String text, PDFont font, float size, Color color, String url) throws IOException {
            for (String paragraph : normalize(text).split("\n", -1)) {
                StringBuilder current = new StringBuilder();
                for (String word : paragraph.strip().split("\\s+")) {
                    if (word.isEmpty()) continue;
                    if (!current.isEmpty() && width(current + " " + word, font, size) <= WIDTH) {
                        current.append(' ').append(word);
                        continue;
                    }
                    if (!current.isEmpty()) { line(current.toString(), font, size, color, url); current.setLength(0); }
                    float measured = 0;
                    for (int cp : word.codePoints().toArray()) {
                        String character = new String(Character.toChars(cp));
                        float advance = width(character, font, size);
                        if (measured + advance > WIDTH && !current.isEmpty()) {
                            line(current.toString(), font, size, color, url); current.setLength(0); measured = 0;
                        }
                        current.append(character); measured += advance;
                    }
                }
                if (!current.isEmpty()) line(current.toString(), font, size, color, url);
                else gap(size / 2);
            }
        }

        private float width(String text, PDFont font, float size) throws IOException { return font.getStringWidth(text) * size / 1000; }

        private void line(String text, PDFont font, float size, Color color, String url) throws IOException {
            ensure(size * 1.5f);
            stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color);
            stream.newLineAtOffset(MARGIN, y); stream.showText(text); stream.endText();
            if (url != null) {
                var annotation = new PDAnnotationLink();
                var border = new PDBorderStyleDictionary(); border.setWidth(0); annotation.setBorderStyle(border);
                annotation.setRectangle(new PDRectangle(MARGIN, y - 2, width(text, font, size), size + 4));
                var action = new PDActionURI(); action.setURI(url); annotation.setAction(action);
                page.getAnnotations().add(annotation);
            }
            y -= size * 1.5f;
        }

        @Override public void close() throws IOException { if (stream != null) stream.close(); }
    }
}
