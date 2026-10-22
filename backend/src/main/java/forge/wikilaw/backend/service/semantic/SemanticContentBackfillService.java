package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SemanticContentBackfillService {
    private final DocumentoSemanticoRepository semanticDocuments;
    private final DecisaoJudicialRepository decisions;
    private final PrecedenteRepository precedents;
    private final DocumentoDoutrinarioRepository doctrine;
    private final SemanticContentService preparation;

    public SemanticContentBackfillService(DocumentoSemanticoRepository semanticDocuments,
            DecisaoJudicialRepository decisions, PrecedenteRepository precedents,
            DocumentoDoutrinarioRepository doctrine, SemanticContentService preparation) {
        this.semanticDocuments = semanticDocuments;
        this.decisions = decisions;
        this.precedents = precedents;
        this.doctrine = doctrine;
        this.preparation = preparation;
    }

    @Transactional
    public int processBatch(int limitPerCategory) {
        int processed = 0;
        for (Long id : semanticDocuments.findDecisoesPendentes(
                SemanticContentService.PREPARATION_VERSION, limitPerCategory)) {
            decisions.findById(id).ifPresent(preparation::prepare);
            processed++;
        }
        for (Long id : semanticDocuments.findPrecedentesPendentes(
                SemanticContentService.PREPARATION_VERSION, limitPerCategory)) {
            precedents.findById(id).ifPresent(preparation::prepare);
            processed++;
        }
        for (Long id : semanticDocuments.findDoutrinasPendentes(
                SemanticContentService.PREPARATION_VERSION, limitPerCategory)) {
            doctrine.findById(id).ifPresent(preparation::prepare);
            processed++;
        }
        return processed;
    }
}

