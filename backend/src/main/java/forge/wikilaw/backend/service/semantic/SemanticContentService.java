package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.repository.DocumentoSemanticoRepository;
import forge.wikilaw.backend.repository.TrechoSemanticoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SemanticContentService {
    public static final String PREPARATION_VERSION = "1";

    private final DocumentoSemanticoRepository documents;
    private final TrechoSemanticoRepository chunks;
    private final LegalDocumentAssembler assembler;
    private final LegalTextChunker chunker;
    private final ObjectMapper json;

    public SemanticContentService(DocumentoSemanticoRepository documents,
            TrechoSemanticoRepository chunks, LegalDocumentAssembler assembler,
            LegalTextChunker chunker, ObjectMapper json) {
        this.documents = documents;
        this.chunks = chunks;
        this.assembler = assembler;
        this.chunker = chunker;
        this.json = json;
    }

    @Transactional
    public PreparationResult prepare(DocumentoBase source) {
        PreparedLegalDocument prepared;
        try {
            prepared = assembler.assemble(source);
        } catch (RuntimeException exception) {
            return recordError(source, exception);
        }

        String metadata = json.writeValueAsString(prepared.metadados());
        List<ChunkDraft> drafts = drafts(prepared);
        if (drafts.isEmpty()) return recordError(source,
                new IllegalArgumentException("Documento sem conteudo semantico util"));

        String contentHash = hash(canonicalContent(prepared, metadata, drafts));
        DocumentoSemantico semantic = find(source).orElseGet(DocumentoSemantico::new);
        if (contentHash.equals(semantic.getHashConteudo())
                && PREPARATION_VERSION.equals(semantic.getVersaoPreparacao())
                && semantic.getStatus() == DocumentoSemantico.Status.PRONTO) {
            return new PreparationResult(semantic.getId(), false, chunks.countByIdDocumentoSemantico(semantic.getId()));
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (semantic.getCriadoEm() == null) semantic.setCriadoEm(now);
        identify(semantic, source);
        semantic.setCategoria(prepared.categoria());
        semantic.setVersaoPreparacao(PREPARATION_VERSION);
        semantic.setStatus(DocumentoSemantico.Status.PROCESSANDO);
        semantic.setHashConteudo(contentHash);
        semantic.setMetadados(metadata);
        semantic.setMensagemErro(null);
        semantic.setAtualizadoEm(now);
        semantic.marcarEmbeddingPendente();
        semantic = documents.saveAndFlush(semantic);

        chunks.deleteByIdDocumentoSemantico(semantic.getId());
        chunks.flush();
        int order = 0;
        var entities = new ArrayList<TrechoSemantico>();
        for (ChunkDraft draft : drafts) {
            var chunk = new TrechoSemantico();
            chunk.setIdDocumentoSemantico(semantic.getId());
            chunk.setOrdem(order++);
            chunk.setSecao(draft.section());
            chunk.setTexto(draft.text());
            chunk.setTextoParaEmbedding(draft.embeddingText());
            chunk.setQuantidadeTokens(chunker.estimateTokens(draft.embeddingText()));
            chunk.setHashTexto(hash(draft.embeddingText()));
            entities.add(chunk);
        }
        chunks.saveAll(entities);
        semantic.setStatus(DocumentoSemantico.Status.PRONTO);
        semantic.setAtualizadoEm(OffsetDateTime.now(ZoneOffset.UTC));
        documents.save(semantic);
        return new PreparationResult(semantic.getId(), true, entities.size());
    }

    @Transactional
    public void deactivate(DocumentoBase source) {
        find(source).ifPresent(semantic -> {
            semantic.setStatus(DocumentoSemantico.Status.INATIVO);
            semantic.setAtualizadoEm(OffsetDateTime.now(ZoneOffset.UTC));
        });
    }

    private PreparationResult recordError(DocumentoBase source, RuntimeException exception) {
        DocumentoSemantico semantic = find(source).orElseGet(DocumentoSemantico::new);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (semantic.getCriadoEm() == null) semantic.setCriadoEm(now);
        identify(semantic, source);
        semantic.setCategoria(category(source));
        semantic.setVersaoPreparacao(PREPARATION_VERSION);
        semantic.setStatus(DocumentoSemantico.Status.ERRO);
        semantic.setHashConteudo(null);
        semantic.setMetadados("{}");
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        semantic.setMensagemErro(message.substring(0, Math.min(message.length(), 500)));
        semantic.setAtualizadoEm(now);
        semantic.marcarEmbeddingPendente();
        semantic = documents.saveAndFlush(semantic);
        chunks.deleteByIdDocumentoSemantico(semantic.getId());
        return new PreparationResult(semantic.getId(), true, 0);
    }

    private List<ChunkDraft> drafts(PreparedLegalDocument document) {
        var result = new ArrayList<ChunkDraft>();
        for (var section : document.secoes()) {
            for (String text : chunker.split(section.texto())) {
                result.add(new ChunkDraft(section.nome(), text, embeddingText(document, section.nome(), text)));
            }
        }
        return result;
    }

    private String embeddingText(PreparedLegalDocument document, String section, String text) {
        var value = new StringBuilder();
        value.append("Categoria: ").append(document.categoria()).append('\n');
        appendContext(value, "Fonte", document.metadados().get("fonte"));
        appendContext(value, "Tribunal", document.metadados().get("tribunal"));
        appendContext(value, "Tipo", document.metadados().get("tipo"));
        value.append("Secao: ").append(section).append('\n');
        if (document.titulo() != null && !document.titulo().equals(text))
            value.append("Titulo: ").append(document.titulo()).append('\n');
        return value.append("Texto:\n").append(text).toString();
    }

    private void appendContext(StringBuilder target, String label, Object value) {
        if (value != null) target.append(label).append(": ").append(value).append('\n');
    }

    private String canonicalContent(PreparedLegalDocument document, String metadata, List<ChunkDraft> drafts) {
        var value = new StringBuilder(PREPARATION_VERSION).append('|')
                .append(chunker.configurationSignature()).append('|')
                .append(document.categoria()).append('|').append(metadata);
        drafts.forEach(chunk -> value.append('|').append(chunk.section())
                .append('|').append(chunk.embeddingText()));
        return value.toString();
    }

    private java.util.Optional<DocumentoSemantico> find(DocumentoBase source) {
        if (source instanceof DecisaoJudicial) return documents.findByIdDecisao(source.getId());
        if (source instanceof Precedente) return documents.findByIdPrecedente(source.getId());
        if (source instanceof DocumentoDoutrinario) return documents.findByIdDocumentoDoutrinario(source.getId());
        throw new IllegalArgumentException("Tipo de documento juridico nao suportado");
    }

    private void identify(DocumentoSemantico target, DocumentoBase source) {
        target.setIdDecisao(null);
        target.setIdPrecedente(null);
        target.setIdDocumentoDoutrinario(null);
        if (source instanceof DecisaoJudicial) target.setIdDecisao(source.getId());
        else if (source instanceof Precedente) target.setIdPrecedente(source.getId());
        else if (source instanceof DocumentoDoutrinario) target.setIdDocumentoDoutrinario(source.getId());
        else throw new IllegalArgumentException("Tipo de documento juridico nao suportado");
    }

    private DocumentoSemantico.Categoria category(DocumentoBase source) {
        if (source instanceof DecisaoJudicial) return DocumentoSemantico.Categoria.JURISPRUDENCIA;
        if (source instanceof Precedente) return DocumentoSemantico.Categoria.PRECEDENTE;
        if (source instanceof DocumentoDoutrinario) return DocumentoSemantico.Categoria.DOUTRINA;
        throw new IllegalArgumentException("Tipo de documento juridico nao suportado");
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel", exception);
        }
    }

    private record ChunkDraft(String section, String text, String embeddingText) {}
    public record PreparationResult(Long semanticDocumentId, boolean changed, long chunks) {}
}

