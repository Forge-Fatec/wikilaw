package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.DocumentoUploadResponse;
import forge.wikilaw.backend.exception.DocumentUploadException;
import forge.wikilaw.backend.service.semantic.LegalTextNormalizer;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_CHARACTERS = 100000;
    private static final int MAX_PDF_PAGES = 200;
    private static final int MAX_EXPANDED_BYTES = 32 * 1024 * 1024;
    private final LegalTextNormalizer normalizer;

    public DocumentUploadService(LegalTextNormalizer normalizer) { this.normalizer = normalizer; }

    public DocumentoUploadResponse extract(MultipartFile file) {
        String name = filename(file.getOriginalFilename());
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("pdf", "docx").contains(extension)) {
            throw problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato não suportado. Envie um arquivo PDF ou DOCX.");
        }
        if (file.isEmpty()) throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "O arquivo está vazio. Selecione outro documento.");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        validateContentType(extension, file.getContentType());
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw tooLarge();
            String text;
            Integer pages = null;
            if (extension.equals("pdf")) {
                if (bytes.length < 5 || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) throw wrongContent();
                try (var pdf = Loader.loadPDF(bytes)) {
                    if (pdf.isEncrypted()) throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "O PDF está protegido. Envie uma cópia sem senha ou restrições.");
                    pages = pdf.getNumberOfPages();
                    if (pages > MAX_PDF_PAGES) throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "O PDF excede o limite de 200 páginas.");
                    var writer = new LimitedTextWriter();
                    var stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    stripper.writeText(pdf, writer);
                    text = writer.toString();
                }
            } else {
                validateDocxArchive(bytes);
                try (var document = new XWPFDocument(new ByteArrayInputStream(bytes));
                        var extractor = new XWPFWordExtractor(document)) {
                    extractor.setFetchHyperlinks(false);
                    text = extractor.getText();
                }
            }
            if (text.length() > MAX_CHARACTERS) throw textTooLong();
            text = normalizer.normalizePlainText(text);
            if (text == null || text.codePoints().noneMatch(Character::isLetterOrDigit)) {
                throw problem(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Não foi encontrado texto no documento. PDFs digitalizados precisam de uma camada de texto (OCR).");
            }
            return new DocumentoUploadResponse(name, extension.toUpperCase(Locale.ROOT), bytes.length, text.length(), pages, text);
        } catch (InvalidPasswordException exception) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "O PDF está protegido por senha. Envie uma cópia sem senha.");
        } catch (DocumentUploadException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "Não foi possível ler o documento. Verifique se ele está íntegro e se é um PDF ou DOCX válido.");
        }
    }

    private void validateContentType(String extension, String contentType) {
        if (contentType == null || contentType.isBlank()) return;
        String mime = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (mime.equals("application/octet-stream")) return;
        boolean valid = extension.equals("pdf") ? mime.equals("application/pdf")
                : Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "application/x-zip-compressed").contains(mime);
        if (!valid) throw wrongContent();
    }

    /** Check actual archive contents and expansion limits before constructing the OOXML model. */
    private void validateDocxArchive(byte[] bytes) throws IOException {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') throw wrongContent();
        int total = 0;
        int entries = 0;
        boolean mainDocument = false;
        boolean wordContentType = false;
        Set<String> names = new HashSet<>();
        try (var archive = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = archive.getNextEntry()) != null) {
                if (++entries > 2000 || !names.add(entry.getName())) throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "A estrutura do DOCX é inválida ou excede os limites de processamento.");
                if (entry.getName().toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) throw wrongContent();
                mainDocument |= entry.getName().equals("word/document.xml");
                var types = entry.getName().equals("[Content_Types].xml") ? new ByteArrayOutputStream() : null;
                int count;
                while ((count = archive.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_EXPANDED_BYTES) throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "O conteúdo descompactado do DOCX excede o limite de 32 MB.");
                    if (types != null) types.write(buffer, 0, count);
                }
                if (types != null) {
                    String content = types.toString(java.nio.charset.StandardCharsets.UTF_8);
                    if (content.toLowerCase(Locale.ROOT).contains("macroenabled")) throw wrongContent();
                    wordContentType = content.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml");
                }
            }
        }
        if (!mainDocument || !wordContentType) throw wrongContent();
    }

    private String filename(String original) {
        if (original == null || original.isBlank()) return "documento";
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.length() > 180) throw problem(HttpStatus.BAD_REQUEST, "O nome do arquivo deve ter até 180 caracteres.");
        return name;
    }

    private static DocumentUploadException problem(HttpStatus status, String message) { return new DocumentUploadException(status, message); }
    private static DocumentUploadException tooLarge() { return problem(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo excede o limite de 10 MB."); }
    private static DocumentUploadException wrongContent() { return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "O conteúdo do arquivo não corresponde a um PDF ou DOCX suportado."); }
    private static DocumentUploadException textTooLong() { return problem(HttpStatus.PAYLOAD_TOO_LARGE, "O texto do documento excede o limite de 100.000 caracteres. Envie um documento menor."); }

    private static class LimitedTextWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        @Override public void write(char[] value, int offset, int length) {
            if (text.length() + length > MAX_CHARACTERS) throw textTooLong();
            text.append(value, offset, length);
        }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString(); }
    }
}
