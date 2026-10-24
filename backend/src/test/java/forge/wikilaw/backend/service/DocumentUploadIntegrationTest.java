package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import forge.wikilaw.backend.dto.DocumentoUploadResponse;
import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.*;
import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wikilaw_upload;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class DocumentUploadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FonteDadosRepository sources;
    @Autowired RegistroBrutoRepository raw;
    @Autowired DocumentRecordProcessor processor;

    private static final String CASE_TEXT = "Indenização por negativação indevida. Proteção do consumidor e reparação por danos morais.";
    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Test void extractsPdfTextWithAccentsAndReturnsTemporaryContext() throws Exception {
        byte[] bytes = pdf(CASE_TEXT, false, 1);
        var result = upload("C:\\fakepath\\caso.PDF", "application/pdf", bytes, 200);
        assertThat(result.formato()).isEqualTo("PDF");
        assertThat(result.nome()).isEqualTo("caso.PDF");
        assertThat(result.paginas()).isEqualTo(1);
        assertThat(result.texto()).contains("negativação indevida", "Proteção do consumidor");
        assertThat(result.caracteres()).isEqualTo(result.texto().length());
        Path directory = Path.of("target", "upload-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("caso.pdf"), bytes);
    }

    @Test void extractsDocxParagraphsAndTables() throws Exception {
        byte[] bytes = docx(CASE_TEXT, true);
        var result = upload("caso.docx", DOCX_MIME, bytes, 200);
        assertThat(result.formato()).isEqualTo("DOCX");
        assertThat(result.paginas()).isNull();
        assertThat(result.texto()).contains(CASE_TEXT, "Contrato", "Negativação");
        Path directory = Path.of("target", "upload-fixtures");
        Files.createDirectories(directory);
        Files.write(directory.resolve("caso.docx"), bytes);
    }

    @Test void validContentWithoutReliableBrowserMimeIsAccepted() throws Exception {
        upload("caso.docx", "application/octet-stream", docx(CASE_TEXT, false), 200);
        upload("caso.pdf", null, pdf(CASE_TEXT, false, 1), 200);
    }

    @Test void unsupportedExtensionsAndDisguisedContentAreRejected() throws Exception {
        upload("caso.txt", "text/plain", CASE_TEXT.getBytes(), 415);
        upload("caso.doc", "application/msword", CASE_TEXT.getBytes(), 415);
        upload("falso.pdf", "application/pdf", CASE_TEXT.getBytes(), 415);
        upload("falso.docx", DOCX_MIME, pdf(CASE_TEXT, false, 1), 415);
        upload("caso.pdf", "text/plain", pdf(CASE_TEXT, false, 1), 415);
        var zip = new ByteArrayOutputStream();
        try (var archive = new ZipOutputStream(zip)) {
            archive.putNextEntry(new ZipEntry("qualquer.txt")); archive.write(CASE_TEXT.getBytes()); archive.closeEntry();
        }
        upload("qualquer.docx", DOCX_MIME, zip.toByteArray(), 415);
    }

    @Test void emptyBrokenEncryptedAndScannedDocumentsHaveActionableErrors() throws Exception {
        upload("vazio.pdf", "application/pdf", new byte[0], 422);
        upload("quebrado.pdf", "application/pdf", "%PDF-1.7\nquebrado".getBytes(), 422);
        upload("protegido.pdf", "application/pdf", pdf(CASE_TEXT, true, 1), 422);
        mvc.perform(multipart("/api/pesquisas/documentos").file(new MockMultipartFile("arquivo", "digitalizado.pdf", "application/pdf", pdf(null, false, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("OCR")));
        upload("vazio.docx", DOCX_MIME, docx("", false), 422);
    }

    @Test void filePageAndExtractedTextLimitsAreEnforced() throws Exception {
        upload("grande.pdf", "application/pdf", new byte[DocumentUploadService.MAX_BYTES + 1], 413);
        upload("paginas.pdf", "application/pdf", pdf(null, false, 201), 413);
        var random = new Random(219);
        var text = new StringBuilder();
        for (int i = 0; i <= DocumentUploadService.MAX_CHARACTERS; i++) text.append((char) ('a' + random.nextInt(26)));
        upload("longo.docx", DOCX_MIME, docx(text.toString(), false), 413);
    }

    @Test void excessiveZipExpansionIsStoppedBeforeParsingWord() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var archive = new ZipOutputStream(bytes)) {
            archive.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] block = new byte[1024 * 1024];
            for (int i = 0; i < 33; i++) archive.write(block);
            archive.closeEntry();
        }
        upload("expansao.docx", DOCX_MIME, bytes.toByteArray(), 413);
    }

    @Test void documentContextActuallyFindsRecordsNotMatchedByTheDescriptionAlone() throws Exception {
        var source = new FonteDados(); source.setNome("Biblioteca de teste"); source.setSigla("BDJUR"); source.setTipoFonte("TESTE");
        source = sources.saveAndFlush(source);
        var record = new RegistroBruto(); record.setFonte(source); record.setFormatoPayload("JSON"); record.setPayloadTexto("{}");
        record.setDataColeta(OffsetDateTime.now()); record.setHashConteudo("f".repeat(64)); record = raw.saveAndFlush(record);
        Long id = processor.process(DocumentSource.BDJUR, source.getId(), record.getId(), NormalizedDocument.builder()
                .identificador("contexto-219").titulo("Usucapião extraordinária e posse prolongada").resumo("Requisitos da posse.").metadados("{}").build());
        mvc.perform(get("/api/documentos/doutrina").param("termo", "atendimento urgente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        String text = "Usucapião extraordinária. Posse prolongada. Usucapião e posse.";
        var extracted = upload("posse.docx", DOCX_MIME, docx(text, false), 200);
        var response = mvc.perform(post("/api/pesquisas/contexto").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("termo", "atendimento urgente", "texto", extracted.texto()))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        String query = json.readValue(response, SearchContextService.PreparedContext.class).consulta();
        assertThat(query).contains("urgente", "usucapião");
        mvc.perform(get("/api/documentos/doutrina").param("termo", query))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(id.intValue()));
    }

    @Test void contextValidationRejectsOversizedEmptyOrNonTextContent() throws Exception {
        mvc.perform(post("/api/pesquisas/contexto").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pesquisas/contexto").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("texto", "a".repeat(100001)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pesquisas/contexto").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"1234567\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private DocumentoUploadResponse upload(String name, String type, byte[] bytes, int expectedStatus) throws Exception {
        var result = mvc.perform(multipart("/api/pesquisas/documentos").file(new MockMultipartFile("arquivo", name, type, bytes)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse();
        if (expectedStatus != 200) return null;
        assertThat(result.getHeader("Cache-Control")).isEqualTo("no-store");
        return json.readValue(result.getContentAsByteArray(), DocumentoUploadResponse.class);
    }

    private byte[] pdf(String text, boolean protectedFile, int pages) throws Exception {
        try (var document = new PDDocument()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            if (text != null) try (var content = new PDPageContentStream(document, document.getPage(0))) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(40, 720); content.showText(text); content.endText();
            }
            if (protectedFile) document.protect(new StandardProtectionPolicy("owner", "secret", new AccessPermission()));
            var output = new ByteArrayOutputStream(); document.save(output); return output.toByteArray();
        }
    }

    private byte[] docx(String text, boolean table) throws Exception {
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            if (table) {
                var row = document.createTable(1, 2).getRow(0);
                row.getCell(0).setText("Contrato"); row.getCell(1).setText("Negativação");
            }
            var output = new ByteArrayOutputStream(); document.write(output); return output.toByteArray();
        }
    }
}
