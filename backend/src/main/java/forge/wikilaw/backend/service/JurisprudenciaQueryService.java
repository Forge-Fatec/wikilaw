package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.JurisprudenciaResumoResponse;
import forge.wikilaw.backend.dto.JurisprudenciaSearchResponse;
import forge.wikilaw.backend.entity.DecisaoJudicial;
import forge.wikilaw.backend.repository.DecisaoJudicialRepository;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import forge.wikilaw.backend.repository.TribunalRepository;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class JurisprudenciaQueryService {

    private static final String[] CAMPOS_PESQUISAVEIS = {
        "titulo", "ementa", "decisao", "numeroProcesso", "relator", "orgaoJulgador"
    };

    private final DecisaoJudicialRepository decisoes;
    private final FonteDadosRepository fontes;
    private final TribunalRepository tribunais;
    private final SearchTermProcessor searchTerms;

    public JurisprudenciaQueryService(
            DecisaoJudicialRepository decisoes,
            FonteDadosRepository fontes,
            TribunalRepository tribunais,
            SearchTermProcessor searchTerms) {
        this.decisoes = decisoes;
        this.fontes = fontes;
        this.tribunais = tribunais;
        this.searchTerms = searchTerms;
    }

    public JurisprudenciaSearchResponse buscar(
            String termo,
            String fonte,
            String tribunal,
            LocalDate dataDe,
            LocalDate dataAte,
            int pagina,
            int tamanho) {
        var resultado = buscarPagina(
                termo, FiltroFontes.uma(fonte), tribunal, dataDe, dataAte, pagina, tamanho);

        Map<Long, String> siglasFontes = new HashMap<>();
        fontes.findAll().forEach(item -> siglasFontes.put(item.getId(), item.getSigla()));
        Map<Long, String> siglasTribunais = new HashMap<>();
        tribunais.findAll().forEach(item -> siglasTribunais.put(item.getId(), item.getSigla()));

        var itens = resultado.getContent().stream()
                .map(decisao -> toResponse(decisao, siglasFontes, siglasTribunais))
                .toList();

        return new JurisprudenciaSearchResponse(
                itens,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    Page<DecisaoJudicial> buscarPagina(
            String termo,
            Collection<String> siglasFonte,
            String tribunal,
            LocalDate dataDe,
            LocalDate dataAte,
            int pagina,
            int tamanho) {
        validarPeriodo(dataDe, dataAte);

        List<Long> idsFonte = FiltroFontes.ids(fontes, siglasFonte);
        Long idTribunal = buscarIdTribunal(tribunal);
        var ordenacao = Sort.by(
                Sort.Order.desc("dataJulgamento").nullsLast(),
                Sort.Order.desc("id"));
        return decisoes.findAll(
                filtro(termo, idsFonte, idTribunal, dataDe, dataAte),
                PageRequest.of(pagina, tamanho, ordenacao));
    }

    /**
     * Mesmo recorte da listagem, para quem precisa agregar em vez de paginar.
     * Fonte ou tribunal fora do cadastro resultam em recorte vazio, não em erro:
     * a tela monta essas opções a partir dos documentos de todas as categorias,
     * então pode citar uma fonte ou tribunal que não tem decisões. Entre várias
     * fontes, as desconhecidas são ignoradas; só sem nenhuma conhecida o recorte fica vazio.
     */
    Optional<Specification<DecisaoJudicial>> recorte(
            String termo,
            Collection<String> siglasFonte,
            String tribunal,
            LocalDate dataDe,
            LocalDate dataAte) {
        validarPeriodo(dataDe, dataAte);

        List<Long> idsFonte = FiltroFontes.idsConhecidos(fontes, siglasFonte);
        if (idsFonte.isEmpty() && !FiltroFontes.normalizar(siglasFonte).isEmpty()) {
            return Optional.empty();
        }

        Long idTribunal = null;
        if (tribunal != null && !tribunal.isBlank()) {
            var encontrado = tribunais.findBySigla(tribunal.trim().toUpperCase(Locale.ROOT));
            if (encontrado.isEmpty()) {
                return Optional.empty();
            }
            idTribunal = encontrado.get().getId();
        }

        return Optional.of(filtro(termo, idsFonte, idTribunal, dataDe, dataAte));
    }

    private Specification<DecisaoJudicial> filtro(
            String termo,
            Collection<Long> idsFonte,
            Long idTribunal,
            LocalDate dataDe,
            LocalDate dataAte) {
        return (root, query, criteriaBuilder) -> {
            var restricoes = new ArrayList<Predicate>();
            restricoes.add(criteriaBuilder.isTrue(root.get("ativo")));

            if (!idsFonte.isEmpty()) {
                restricoes.add(root.get("idFonte").in(idsFonte));
            }
            if (idTribunal != null) {
                restricoes.add(criteriaBuilder.equal(root.get("idTribunal"), idTribunal));
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

    private Predicate like(
            jakarta.persistence.criteria.Root<DecisaoJudicial> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String campo,
            String padrao) {
        return criteriaBuilder.like(criteriaBuilder.lower(root.get(campo)), padrao, '\\');
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

    private JurisprudenciaResumoResponse toResponse(
            DecisaoJudicial decisao,
            Map<Long, String> siglasFontes,
            Map<Long, String> siglasTribunais) {
        return new JurisprudenciaResumoResponse(
                decisao.getId(),
                siglasFontes.get(decisao.getIdFonte()),
                siglasTribunais.get(decisao.getIdTribunal()),
                decisao.getIdentificadorExterno(),
                decisao.getNumeroProcesso(),
                decisao.getTipoDecisao(),
                decisao.getTitulo(),
                decisao.getEmenta(),
                decisao.getDecisao(),
                decisao.getRelator(),
                decisao.getOrgaoJulgador(),
                decisao.isPossuiInteiroTeor(),
                decisao.getDataJulgamento(),
                decisao.getDataPublicacao(),
                decisao.getDataOriginal(),
                decisao.getUrlOriginal());
    }
}
