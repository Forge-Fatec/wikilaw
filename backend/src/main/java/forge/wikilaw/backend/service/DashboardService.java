package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.DashboardResponse;
import forge.wikilaw.backend.dto.DashboardResponse.DistribuicaoItem;
import forge.wikilaw.backend.dto.DashboardResponse.Indicadores;
import forge.wikilaw.backend.entity.DecisaoJudicial;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores e distribuições das decisões que entram no recorte da pesquisa
 * ativa. Só jurisprudência entra na conta: em precedentes o campo "decisao"
 * traz a tese, não o resultado do recurso.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int LIMITE_ORGAOS = 8;
    static final String ORGAO_NAO_INFORMADO = "Não informado";
    static final String OUTROS_ORGAOS = "Outros";

    private final JurisprudenciaQueryService jurisprudencia;
    private final EntityManager entityManager;

    public DashboardService(JurisprudenciaQueryService jurisprudencia, EntityManager entityManager) {
        this.jurisprudencia = jurisprudencia;
        this.entityManager = entityManager;
    }

    public DashboardResponse resumir(
            String termo,
            String fonte,
            String tribunal,
            LocalDate dataDe,
            LocalDate dataAte) {
        var decisoes = jurisprudencia.recorte(termo, fonte, tribunal, dataDe, dataAte)
                .map(this::carregar)
                .orElse(List.of());

        Map<ResultadoDecisao, Long> porResultado = new EnumMap<>(ResultadoDecisao.class);
        Arrays.stream(ResultadoDecisao.values()).forEach(resultado -> porResultado.put(resultado, 0L));
        Map<String, Long> porOrgao = new HashMap<>();

        for (Tuple decisao : decisoes) {
            porResultado.merge(ResultadoDecisao.classificar(decisao.get(1, String.class)), 1L, Long::sum);
            porOrgao.merge(nomeDoOrgao(decisao.get(0, String.class)), 1L, Long::sum);
        }

        return new DashboardResponse(
                indicadores(decisoes.size(), porResultado),
                porResultado.entrySet().stream()
                        .map(item -> new DistribuicaoItem(item.getKey().rotulo(), item.getValue()))
                        .toList(),
                distribuicaoPorOrgao(porOrgao));
    }

    // Só as duas colunas agregadas: carregar a entidade traria ementa e inteiro teor.
    private List<Tuple> carregar(Specification<DecisaoJudicial> recorte) {
        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var query = criteriaBuilder.createTupleQuery();
        var root = query.from(DecisaoJudicial.class);
        query.multiselect(root.get("orgaoJulgador"), root.get("decisao"))
                .where(recorte.toPredicate(root, query, criteriaBuilder));
        return entityManager.createQuery(query).getResultList();
    }

    private Indicadores indicadores(long total, Map<ResultadoDecisao, Long> porResultado) {
        long classificadas = total - porResultado.get(ResultadoDecisao.INDEFINIDO);
        long favoraveis = porResultado.get(ResultadoDecisao.PROVIDO) + porResultado.get(ResultadoDecisao.PARCIAL);
        Integer aderencia = classificadas == 0
                ? null
                : (int) Math.round(favoraveis * 100.0 / classificadas);
        return new Indicadores(total, classificadas, favoraveis, aderencia);
    }

    private String nomeDoOrgao(String orgaoJulgador) {
        if (orgaoJulgador == null || orgaoJulgador.isBlank()) {
            return ORGAO_NAO_INFORMADO;
        }
        return orgaoJulgador.trim().replaceAll("\\s+", " ");
    }

    // Os órgãos com mais decisões aparecem nominalmente; o restante vira "Outros"
    // para o eixo do gráfico continuar legível.
    private List<DistribuicaoItem> distribuicaoPorOrgao(Map<String, Long> porOrgao) {
        var ordenados = porOrgao.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(item -> new DistribuicaoItem(item.getKey(), item.getValue()))
                .toList();
        if (ordenados.size() <= LIMITE_ORGAOS) {
            return ordenados;
        }

        var distribuicao = new ArrayList<>(ordenados.subList(0, LIMITE_ORGAOS - 1));
        long demais = ordenados.subList(LIMITE_ORGAOS - 1, ordenados.size()).stream()
                .mapToLong(DistribuicaoItem::quantidade)
                .sum();
        distribuicao.add(new DistribuicaoItem(OUTROS_ORGAOS, demais));
        return distribuicao;
    }
}
