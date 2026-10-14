package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import forge.wikilaw.backend.dto.PesquisaPdfRequest;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wikilaw_pdf;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class PesquisaPdfIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DocumentRecordProcessor processor;
    @Autowired FonteDadosRepository sources;
    @Autowired RegistroBrutoRepository raw;
    @Autowired TribunalRepository tribunals;

    @Test
    void exportsCanonicalContentOfAllCategoriesInRequestedOrderAndDownloadsPdf() throws Exception {
        var decision = document(DocumentSource.STJ, "decisão", "Acórdão sobre reparação de danos", "Ementa: proteção do consumidor.", "https://www.stj.jus.br/acordao/123");
        var precedent = document(DocumentSource.STJ_PRECEDENTES, "tema", "Tema repetitivo 123", "Questão jurídica submetida ao STJ.", null);
        var doctrine = document(DocumentSource.BDJUR, "artigo", "Doutrina sobre indenização", "Resumo do artigo científico.", "javascript:alert(1)");
        var response = mvc.perform(post("/api/pesquisas/exportacoes/pdf").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request(List.of(doctrine, decision, precedent)))))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment; filename=\"wikilaw-pesquisa-")))
                .andReturn().getResponse();
        byte[] bytes = response.getContentAsByteArray();
        assertThat(new String(Arrays.copyOf(bytes, 5), java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var pdf = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Dados da pesquisa", "negativação indevida", "TJSP", "01/01/2024", "Resultados exportados", "3", "Doutrina sobre indenização", "Proteção ao consumidor", "Recurso provido", "Tese firmada", "Maria Silva", "Revista Jurídica", "10.1234/teste", "Página 1 de");
            assertThat(text.indexOf("Doutrina sobre indenização")).isLessThan(text.indexOf("Acórdão sobre reparação"));
            assertThat(text.indexOf("Acórdão sobre reparação")).isLessThan(text.indexOf("Tema repetitivo"));
            List<String> links = new ArrayList<>();
            for (var page : pdf.getPages()) for (var annotation : page.getAnnotations()) {
                if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI uri) links.add(uri.getURI());
            }
            assertThat(links).contains("https://www.stj.jus.br/acordao/123").noneMatch(value -> value.startsWith("javascript:"));
            for (var page : pdf.getPages()) for (var name : page.getResources().getFontNames()) {
                assertThat(page.getResources().getFont(name).isEmbedded()).isTrue();
            }
            Path directory = Path.of("target", "pdf-preview");
            Files.createDirectories(directory);
            Files.write(directory.resolve("wikilaw-pesquisa-exemplo.pdf"), bytes);
            var renderer = new PDFRenderer(pdf);
            for (int i = 0; i < pdf.getNumberOfPages(); i++) ImageIO.write(renderer.renderImageWithDPI(i, 100), "PNG", directory.resolve("example-page-" + (i + 1) + ".png").toFile());
        }
    }

    @Test
    void longContentAndLongUrlsContinueAcrossPagesWithoutTruncation() throws Exception {
        String text = ("Jurisprudência: reparação à vítima e proteção do consumidor.\n").repeat(160) + "MARCADOR FINAL ⚖";
        var ref = document(DocumentSource.STJ, "longo", "Texto longo", text, "https://www.stj.jus.br/" + "a".repeat(900));
        byte[] bytes = export(request(List.of(ref)), 200);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            String extracted = new PDFTextStripper().getText(pdf);
            assertThat(extracted).contains("MARCADOR FINAL", "proteção", "Página " + pdf.getNumberOfPages() + " de " + pdf.getNumberOfPages());
            var renderer = new PDFRenderer(pdf);
            Path directory = Path.of("target", "pdf-preview");
            Files.createDirectories(directory);
            ImageIO.write(renderer.renderImageWithDPI(1, 100), "PNG", directory.resolve("long-page-2.png").toFile());
            ImageIO.write(renderer.renderImageWithDPI(pdf.getNumberOfPages() - 1, 100), "PNG", directory.resolve("long-page-last.png").toFile());
        }
    }

    @Test
    void exportsBeyondAQueryBatchAndTheUiPageWithoutDroppingDocuments() throws Exception {
        List<DocumentoReferencia> references = new ArrayList<>();
        for (int i = 0; i < 251; i++) references.add(document(DocumentSource.BDJUR, "artigo-" + i, "Documento número " + i, "Resumo.", null));
        Collections.reverse(references);
        try (var pdf = Loader.loadPDF(export(request(references), 200))) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Documento número 250", "Documento número 0", "251");
            assertThat(text.indexOf("Documento número 250")).isLessThan(text.indexOf("Documento número 0"));
        }
    }

    @Test void emptyResultsAreRejected() throws Exception { export(request(List.of()), 400); }
    @Test void duplicateReferencesAreRejected() throws Exception {
        var ref = document(DocumentSource.BDJUR, "dup", "Artigo", "Resumo", null);
        export(request(List.of(ref, ref)), 400);
    }
    @Test void incompatibleCategoriesAreRejected() throws Exception {
        var ref = document(DocumentSource.BDJUR, "categoria", "Artigo", "Resumo", null);
        export(new PesquisaPdfRequest("caso", List.of("decisoes"), null, null, null, null, "mais-recentes", List.of(ref)), 400);
    }
    @Test void missingOrWithdrawnDocumentsDoNotProduceAnIncompletePdf() throws Exception {
        export(request(List.of(new DocumentoReferencia("doutrina", 999999L))), 409);
        var ref = document(DocumentSource.BDJUR, "retirado", "Artigo", "Resumo", null);
        processor.indisponibilizar(DocumentSource.BDJUR, sources.findBySigla("BDJUR").orElseThrow().getId(), "retirado");
        export(request(List.of(ref)), 409);
    }
    @Test void invalidPeriodAndUnknownCategoryAreRejected() throws Exception {
        var ref = new DocumentoReferencia("doutrina", 1L);
        export(new PesquisaPdfRequest("caso", List.of("doutrina"), null, null, LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1), "mais-recentes", List.of(ref)), 400);
        export(request(List.of(new DocumentoReferencia("desconhecida", 1L))), 400);
    }
    @Test void excessiveResultCountIsRejectedWithoutTruncation() throws Exception {
        export(request(Collections.nCopies(5001, new DocumentoReferencia("doutrina", 1L))), 400);
    }
    @Test void corsAllowsDownloadAndExposesTheFilename() throws Exception {
        var ref = document(DocumentSource.BDJUR, "cors", "Artigo", "Resumo", null);
        mvc.perform(post("/api/pesquisas/exportacoes/pdf").header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request(List.of(ref)))))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"));
    }

    private byte[] export(PesquisaPdfRequest request, int status) throws Exception {
        return mvc.perform(post("/api/pesquisas/exportacoes/pdf").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsByteArray();
    }
    private PesquisaPdfRequest request(List<DocumentoReferencia> references) {
        return new PesquisaPdfRequest("negativação indevida", List.of("decisoes", "precedentes", "doutrina"), null, "TJSP",
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31), "mais-recentes", references);
    }
    private DocumentoReferencia document(DocumentSource source, String identifier, String title, String summary, String url) {
        var sourceRecord = sources.findBySigla(source.sigla()).orElseGet(() -> {
            var record = new FonteDados(); record.setSigla(source.sigla()); record.setNome(source.sigla()); record.setTipoFonte("TESTE");
            return sources.saveAndFlush(record);
        });
        if (tribunals.findBySigla("STJ").isEmpty()) {
            var tribunal = new Tribunal(); tribunal.setSigla("STJ"); tribunal.setNome("Superior Tribunal de Justiça"); tribunals.saveAndFlush(tribunal);
        }
        var rawRecord = new RegistroBruto(); rawRecord.setFonte(sourceRecord); rawRecord.setFormatoPayload("JSON");
        rawRecord.setPayloadTexto("{}"); rawRecord.setDataColeta(OffsetDateTime.now()); rawRecord.setHashConteudo("d".repeat(64));
        rawRecord = raw.saveAndFlush(rawRecord);
        Long id = processor.process(source, sourceRecord.getId(), rawRecord.getId(), NormalizedDocument.builder()
                .identificador(identifier).titulo(title).resumo(summary).tipo("Artigo / Acórdão")
                .decisao("Recurso provido").tese("Proteção ao consumidor").autores("Maria Silva").relator("Ministro Exemplo")
                .orgaoJulgador("Segunda Turma").numeroTema("123").numeroProcesso("REsp 123").periodico("Revista Jurídica")
                .doi("10.1234/teste").dataPublicacao(LocalDate.of(2025, 4, 10)).url(url).metadados("{}").build());
        return new DocumentoReferencia(source == DocumentSource.STJ ? "decisoes" : source == DocumentSource.STJ_PRECEDENTES ? "precedentes" : "doutrina", id);
    }
}
