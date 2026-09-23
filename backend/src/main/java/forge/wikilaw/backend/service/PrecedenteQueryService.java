package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.PrecedenteResumoResponse;
import forge.wikilaw.backend.dto.PrecedenteSearchResponse;
import forge.wikilaw.backend.entity.Precedente;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import forge.wikilaw.backend.repository.PrecedenteRepository;
import forge.wikilaw.backend.repository.TribunalRepository;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Busca de precedentes qualificados (temas repetitivos, repercussao geral, IAC/IRDR).
 *
 * <p>Espelha {@link JurisprudenciaQueryService}: mesma tokenizacao via
 * {@link SearchTermProcessor}, mesmos filtros de fonte/tribunal/periodo e a mesma
 * validacao de periodo invertido, para que as duas categorias respondam de forma
 * previsivel ao mesmo conjunto de parametros.
 *
 * <p>Os campos pesquisaveis sao os proprios do precedente: alem do titulo, a
 * questao juridica submetida, a tese firmada, a situacao, o numero do tema e o
 * tipo. Isso cobre tanto a busca conceitual ("prescricao tributaria") quanto a
 * busca direta por identificacao ("Tema 1234", "repetitivo").
 */
@Service
@Transactional(readOnly = true)
public class PrecedenteQueryService {

    private static final String[] CAMPOS_PESQUISAVEIS = {
        "titulo", "questaoJuridica", "tese", "situacao", "numeroTema", "tipoPrecedente"
    };

    private final PrecedenteRepository precedentes;
    private final FonteDadosRepository fontes;
    private final TribunalRepository tribunais;
    private final SearchTermProcessor searchTerms;

    public PrecedenteQueryService(
            PrecedenteRepository precedentes,
            FonteDadosRepository fontes,
            TribunalRepository tribunais,
            SearchTermProcessor searchTerms) {
        this.precedentes = precedentes;
        this.fontes = fontes;
        this.tribunais = tribunais;
        this.searchTerms = searchTerms;
    }

    public PrecedenteSearchResponse buscar(
            String termo,
            String fonte,
            String tribunal,
            String tipo,
            String situacao,
            LocalDate dataDe,
            LocalDate dataAte,
            int pagina,
            int tamanho) {
        var resultado = buscarPagina(
                termo, fonte, tribunal, tipo, situacao, dataDe, dataAte, pagina, tamanho);

        Map<Long, String> siglasFontes = new HashMap<>();
        fontes.findAll().forEach(item -> siglasFontes.put(item.getId(), item.getSigla()));
        Map<Long, String> siglasTribunais = new HashMap<>();
        tribunais.findAll().forEach(item -> siglasTribunais.put(item.getId(), item.getSigla()));

        var itens = resultado.getContent().stream()
                .map(precedente -> toResponse(precedente, siglasFontes, siglasTribunais))
                .toList();

        return new PrecedenteSearchResponse(
                itens,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    Page<Precedente> buscarPagina(
            String termo,
            String fonte,
            String tribunal,
            String tipo,
            String situacao,
            LocalDate dataDe,
            LocalDate dataAte,
            int pagina,
            int tamanho) {
        validarPeriodo(dataDe, dataAte);

        Long idFonte = buscarIdFonte(fonte);
        Long idTribunal = buscarIdTribunal(tribunal);
        // Tema apenas afetado ainda nao tem data de julgamento; nullsLast evita
        // que esses registros ocupem o topo da lista por causa do NULL.
        var ordenacao = Sort.by(
                Sort.Order.desc("dataJulgamento").nullsLast(),
                Sort.Order.desc("id"));
        return precedentes.findAll(
                filtro(termo, idFonte, idTribunal, tipo, situacao, dataDe, dataAte),
                PageRequest.of(pagina, tamanho, ordenacao));
    }

    private Specification<Precedente> filtro(
            String termo,
            Long idFonte,
            Long idTribunal,
            String tipo,
            String situacao,
            LocalDate dataDe,
            LocalDate dataAte) {
        return (root, query, criteriaBuilder) -> {
            var restricoes = new ArrayList<Predicate>();
            restricoes.add(criteriaBuilder.isTrue(root.get("ativo")));

            if (idFonte != null) {
                restricoes.add(criteriaBuilder.equal(root.get("idFonte"), idFonte));
            }
            if (idTribunal != null) {
                restricoes.add(criteriaBuilder.equal(root.get("idTribunal"), idTribunal));
            }
            if (tipo != null && !tipo.isBlank()) {
                restricoes.add(like(root, criteriaBuilder, "tipoPrecedente", padraoDeFiltro(tipo)));
            }
            if (situacao != null && !situacao.isBlank()) {
                restricoes.add(like(root, criteriaBuilder, "situacao", padraoDeFiltro(situacao)));
            }
            if (dataDe != null) {
                restricoes.add(criteriaBuilder.greaterThanOrEqualTo(root.get("dataJulgamento"), dataDe));
            }
            if (dataAte != null) {
                restricoes.add(criteriaBuilder.lessThanOrEqualTo(root.get("dataJulgamento"), dataAte));
            }
            if (termo != null && !termo.isBlank()) {
                var alternativas = new ArrayList<Predicate>();
                for (String palavra : searchTerms.tokenize(termo)) {
                    String padrao = searchTerms.containsPattern(palavra);
                    for (String campo : CAMPOS_PESQUISAVEIS) {
                        alternativas.add(like(root, criteriaBuilder, campo, padrao));
                    }
                }
                restricoes.add(criteriaBuilder.or(alternativas.toArray(Predicate[]::new)));
            }

            return criteriaBuilder.and(restricoes.toArray(Predicate[]::new));
        };
    }

    /** Tipo e situacao aceitam parte do valor da fonte ("repetitivo", "transito"). */
    private String padraoDeFiltro(String valor) {
        return searchTerms.containsPattern(valor.trim().toLowerCase(Locale.ROOT));
    }

    private Predicate like(
            Root<Precedente> root,
            CriteriaBuilder criteriaBuilder,
            String campo,
            String padrao) {
        return criteriaBuilder.like(criteriaBuilder.lower(root.get(campo)), padrao, '\\');
    }

    private Long buscarIdFonte(String fonte) {
        if (fonte == null || fonte.isBlank()) {
            return null;
        }
        return fontes.findBySigla(fonte.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fonte desconhecida"))
                .getId();
    }

    private Long buscarIdTribunal(String tribunal) {
        if (tribunal == null || tribunal.isBlank()) {
            return null;
        }
        return tribunais.findBySigla(tribunal.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tribunal desconhecido"))
                .getId();
    }

    private void validarPeriodo(LocalDate dataDe, LocalDate dataAte) {
        if (dataDe != null && dataAte != null && dataDe.isAfter(dataAte)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "dataDe deve ser anterior ou igual a dataAte");
        }
    }

    private PrecedenteResumoResponse toResponse(
            Precedente precedente,
            Map<Long, String> siglasFontes,
            Map<Long, String> siglasTribunais) {
        return new PrecedenteResumoResponse(
                precedente.getId(),
                siglasFontes.get(precedente.getIdFonte()),
                siglasTribunais.get(precedente.getIdTribunal()),
                precedente.getNumeroTema(),
                precedente.getTipoPrecedente(),
                precedente.getTitulo(),
                precedente.getQuestaoJuridica(),
                precedente.getTese(),
                precedente.getSituacao(),
                precedente.getDataJulgamento(),
                precedente.getDataPublicacao(),
                precedente.getUrlOriginal());
    }
}
