package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import forge.wikilaw.backend.repository.TribunalRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LegalDocumentAssembler {
    private final LegalTextNormalizer normalizer;
    private final FonteDadosRepository fontes;
    private final TribunalRepository tribunais;

    public LegalDocumentAssembler(LegalTextNormalizer normalizer,
            FonteDadosRepository fontes, TribunalRepository tribunais) {
        this.normalizer = normalizer;
        this.fontes = fontes;
        this.tribunais = tribunais;
    }

    public PreparedLegalDocument assemble(DocumentoBase document) {
        String title = normalizer.normalize(document.getTitulo());
        var metadata = new LinkedHashMap<String, Object>();
        put(metadata, "identificadorExterno", document.getIdentificadorExterno());
        fontes.findById(document.getIdFonte()).ifPresent(source -> put(metadata, "fonte", source.getSigla()));
        put(metadata, "dataPublicacao", date(document.getDataPublicacao()));
        put(metadata, "urlOriginal", document.getUrlOriginal());

        var sections = new ArrayList<PreparedLegalDocument.Section>();
        var seen = new LinkedHashSet<String>();
        DocumentoSemantico.Categoria category;
        if (document instanceof DecisaoJudicial decision) {
            category = DocumentoSemantico.Categoria.JURISPRUDENCIA;
            put(metadata, "tipo", decision.getTipoDecisao());
            put(metadata, "numeroProcesso", decision.getNumeroProcesso());
            put(metadata, "relator", decision.getRelator());
            put(metadata, "orgaoJulgador", decision.getOrgaoJulgador());
            put(metadata, "tribunal", tribunal(decision.getIdTribunal()));
            put(metadata, "dataJulgamento", date(decision.getDataJulgamento()));
            add(sections, seen, "EMENTA", decision.getEmenta());
            add(sections, seen, "DECISAO", decision.getDecisao());
            add(sections, seen, "INTEIRO_TEOR", decision.getInteiroTeor());
        } else if (document instanceof Precedente precedent) {
            category = DocumentoSemantico.Categoria.PRECEDENTE;
            put(metadata, "tipo", precedent.getTipoPrecedente());
            put(metadata, "numeroTema", precedent.getNumeroTema());
            put(metadata, "situacao", precedent.getSituacao());
            put(metadata, "tribunal", tribunal(precedent));
            put(metadata, "dataJulgamento", date(precedent.getDataJulgamento()));
            add(sections, seen, "QUESTAO_JURIDICA", precedent.getQuestaoJuridica());
            add(sections, seen, "TESE", precedent.getTese());
        } else if (document instanceof DocumentoDoutrinario doctrine) {
            category = DocumentoSemantico.Categoria.DOUTRINA;
            put(metadata, "tipo", doctrine.getTipoDocumento());
            put(metadata, "autores", doctrine.getAutores());
            put(metadata, "periodico", doctrine.getPeriodico());
            put(metadata, "doi", doctrine.getDoi());
            put(metadata, "idioma", doctrine.getIdioma());
            add(sections, seen, "RESUMO", doctrine.getResumo());
            add(sections, seen, "PALAVRAS_CHAVE", doctrine.getPalavrasChave());
        } else {
            throw new IllegalArgumentException("Tipo de documento juridico nao suportado");
        }
        metadata.put("categoria", category.name());
        if (sections.isEmpty() && title != null) sections.add(new PreparedLegalDocument.Section("TITULO", title));
        return new PreparedLegalDocument(category, title, metadata, sections);
    }

    private void add(List<PreparedLegalDocument.Section> sections, Set<String> seen,
            String name, String rawText) {
        String text = normalizer.normalize(rawText);
        if (text != null && seen.add(text)) sections.add(new PreparedLegalDocument.Section(name, text));
    }

    private String tribunal(Long id) {
        return id == null ? null : tribunais.findById(id).map(Tribunal::getSigla).orElse(null);
    }

    private String tribunal(Precedente precedent) {
        String registered = tribunal(precedent.getIdTribunal());
        return registered != null ? registered : precedent.getTribunalOrigem();
    }

    private String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value instanceof String text) {
            String normalized = normalizer.normalize(text);
            if (normalized != null) target.put(key, normalized);
        } else if (value != null) {
            target.put(key, value);
        }
    }
}

