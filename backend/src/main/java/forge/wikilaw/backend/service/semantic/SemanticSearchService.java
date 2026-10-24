package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.entity.DocumentoSemantico.Categoria;
import forge.wikilaw.backend.service.DocumentQueryService;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.semantic.SemanticVectorRetrievalService.SimilarDocument;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Busca semantica de ponta a ponta: o caso vira vetor, os vetores mais proximos sao recuperados e cada documento
 * volta com o mesmo resumo da busca por palavras, mais a similaridade e o trecho que justifica o resultado.
 */
@Service
public class SemanticSearchService {
    private final SemanticCaseRepresentationService representation;
    private final SemanticVectorRetrievalService retrieval;
    private final DocumentQueryService documents;
    private final SimilarityScorer scorer;

    public SemanticSearchService(SemanticCaseRepresentationService representation,
            SemanticVectorRetrievalService retrieval, DocumentQueryService documents, SimilarityScorer scorer) {
        this.representation = representation;
        this.retrieval = retrieval;
        this.documents = documents;
        this.scorer = scorer;
    }

    public SemanticSearchResult buscar(SemanticCaseQuery query, Collection<Categoria> categories, int limit) {
        var caseRepresentation = representation.representar(query);
        List<SimilarDocument> similar = retrieval.recuperar(caseRepresentation.vetor(), categories, limit);

        Map<Categoria, List<Long>> idsByCategory = new EnumMap<>(Categoria.class);
        similar.forEach(document -> idsByCategory.computeIfAbsent(document.categoria(), c -> new ArrayList<>())
                .add(sourceId(document)));
        Map<String, Summary> summaries = new HashMap<>();
        idsByCategory.forEach((category, ids) -> documents.summariesByIds(apiCategory(category), ids)
                .forEach(summary -> summaries.put(key(category, summary.id()), summary)));

        var items = new ArrayList<SemanticSearchItem>(similar.size());
        for (SimilarDocument document : similar) {
            // Documento retirado da fonte depois de indexado nao tem resumo exibivel e fica de fora.
            Summary summary = summaries.get(key(document.categoria(), sourceId(document)));
            if (summary == null) continue;
            items.add(new SemanticSearchItem(summary, document.similaridade(),
                    scorer.percentual(document.similaridade()), document.trechosSemelhantes(),
                    new RelevantChunk(document.secaoRelevante(), document.trechoRelevante()),
                    scorer.justificativa(document.secaoRelevante(), document.trechosSemelhantes())));
        }
        return new SemanticSearchResult(caseRepresentation.modelo(), List.copyOf(items));
    }

    /** Mesmos nomes de categoria de {@code GET /api/documentos/{categoria}}. */
    public static String apiCategory(Categoria category) {
        return switch (category) {
            case JURISPRUDENCIA -> "decisoes";
            case PRECEDENTE -> "precedentes";
            case DOUTRINA -> "doutrina";
        };
    }

    public static Categoria fromApiCategory(String value) {
        return switch (value) {
            case "decisoes" -> Categoria.JURISPRUDENCIA;
            case "precedentes" -> Categoria.PRECEDENTE;
            case "doutrina" -> Categoria.DOUTRINA;
            default -> throw new IllegalArgumentException("Categoria invalida: " + value);
        };
    }

    private Long sourceId(SimilarDocument document) {
        return switch (document.categoria()) {
            case JURISPRUDENCIA -> document.idDecisao();
            case PRECEDENTE -> document.idPrecedente();
            case DOUTRINA -> document.idDocumentoDoutrinario();
        };
    }

    private String key(Categoria category, Long id) {
        return category.name() + ':' + id;
    }

    public record RelevantChunk(String secao, String texto) {}

    /** {@code percentual} e a similaridade calibrada de 0 a 100, a que a tela exibe e usa no limite de 50%. */
    public record SemanticSearchItem(Summary documento, double similaridade, int percentual, int trechosSemelhantes,
            RelevantChunk trechoRelevante, String justificativa) {}

    public record SemanticSearchResult(String modelo, List<SemanticSearchItem> resultados) {}
}