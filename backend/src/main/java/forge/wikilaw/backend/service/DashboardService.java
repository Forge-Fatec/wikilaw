package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.DashboardFiltro;
import forge.wikilaw.backend.dto.DashboardResponse;
import forge.wikilaw.backend.dto.DashboardResponse.DistribuicaoItem;
import forge.wikilaw.backend.dto.DashboardResponse.Indicadores;
import forge.wikilaw.backend.entity.DecisaoJudicial;
import forge.wikilaw.backend.repository.TribunalRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores e agregações das decisões que entram no recorte da pesquisa
 * ativa. Só jurisprudência entra na conta: em precedentes o campo "decisao"
 * traz a tese, não o resultado do recurso.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int LIMITE_CATEGORIAS = 8;
    static final String NAO_INFORMADO = "Não informado";
    static final String OUTROS = "Outros";
    static final String SEM_DATA = "Sem data";

    private final JurisprudenciaQueryService jurisprudencia;
    private final TribunalRepository tribunais;
    private final EntityManager entityManager;

    public DashboardService(
            JurisprudenciaQueryService jurisprudencia,
            TribunalRepository tribunais,
            EntityManager entityManager) {
        this.jurisprudencia = jurisprudencia;
        this.tribunais = tribunais;
        this.entityManager = entityManager;
    }

    // Cada gráfico ignora a própria seleção: continua com todas as barras (a
    // escolhida fica em destaque na tela) e só a seleção do outro o recorta.
    public DashboardResponse resumir(DashboardFiltro filtro) {
        return resumir(carregar(filtro), filtro);
    }

    public DashboardResponse resumirIds(List<Long> ids, String resultado, String orgaoJulgador) {
        Specification<DecisaoJudicial> recorte = (root, query, cb) -> cb.and(
                cb.isTrue(root.get("ativo")), root.get("id").in(ids));
        return resumir(ids.isEmpty() ? List.of() : consultar(recorte),
                new DashboardFiltro(null,null,null,null,null,resultado,orgaoJulgador));
    }

    private DashboardResponse resumir(List<Decisao> decisoes, DashboardFiltro filtro) {
        var selecao = Selecao.de(filtro);
        var selecionadas = selecao.aplicar(decisoes);
        return new DashboardResponse(
                indicadores(selecionadas),
                distribuicaoMerito(selecao.semResultado().aplicar(decisoes)),
                distribuicaoCamaraTurma(selecao.semOrgaoJulgador().aplicar(decisoes)),
                distribuicaoTribunal(selecionadas),
                evolucaoAnual(selecionadas));
    }

    public Indicadores indicadores(DashboardFiltro filtro) {
        return indicadores(Selecao.de(filtro).aplicar(carregar(filtro)));
    }

    public List<DistribuicaoItem> distribuicaoMerito(DashboardFiltro filtro) {
        return distribuicaoMerito(Selecao.de(filtro).semResultado().aplicar(carregar(filtro)));
    }

    public List<DistribuicaoItem> distribuicaoCamaraTurma(DashboardFiltro filtro) {
        return distribuicaoCamaraTurma(Selecao.de(filtro).semOrgaoJulgador().aplicar(carregar(filtro)));
    }

    public List<DistribuicaoItem> distribuicaoTribunal(DashboardFiltro filtro) {
        return distribuicaoTribunal(Selecao.de(filtro).aplicar(carregar(filtro)));
    }

    public List<DistribuicaoItem> evolucaoAnual(DashboardFiltro filtro) {
        return evolucaoAnual(Selecao.de(filtro).aplicar(carregar(filtro)));
    }

    /** Só as colunas agregadas: carregar a entidade traria ementa e inteiro teor. */
    private record Decisao(String orgaoJulgador, String decisao, Long idTribunal, LocalDate dataJulgamento) {
    }

    /**
     * Categorias selecionadas nos gráficos, pelo rótulo da barra; null quando
     * o gráfico não tem seleção. Rótulo desconhecido não casa com nenhuma decisão.
     */
    private record Selecao(String resultado, String orgaoJulgador) {

        static Selecao de(DashboardFiltro filtro) {
            return new Selecao(textoOuNulo(filtro.resultado()), textoOuNulo(filtro.orgaoJulgador()));
        }

        Selecao semResultado() {
            return new Selecao(null, orgaoJulgador);
        }

        Selecao semOrgaoJulgador() {
            return new Selecao(resultado, null);
        }

        List<Decisao> aplicar(List<Decisao> decisoes) {
            if (resultado == null && orgaoJulgador == null) {
                return decisoes;
            }
            var resultadoSelecionado = resultado == null ? null : ResultadoDecisao.doRotulo(resultado);
            var orgaoSelecionado = orgaoJulgador == null ? null : nomeOuNaoInformado(orgaoJulgador);
            return decisoes.stream()
                    .filter(decisao -> resultadoSelecionado == null
                            || resultadoSelecionado.equals(Optional.of(ResultadoDecisao.classificar(decisao.decisao()))))
                    .filter(decisao -> orgaoSelecionado == null
                            || orgaoSelecionado.equals(nomeOuNaoInformado(decisao.orgaoJulgador())))
                    .toList();
        }

        private static String textoOuNulo(String valor) {
            return valor == null || valor.isBlank() ? null : valor;
        }
    }

    private List<Decisao> carregar(DashboardFiltro filtro) {
        return jurisprudencia.recorte(
                        filtro.termo(), filtro.fonte(), filtro.tribunal(), filtro.dataDe(), filtro.dataAte())
                .map(this::consultar)
                .orElse(List.of());
    }

    private List<Decisao> consultar(Specification<DecisaoJudicial> recorte) {
        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var query = criteriaBuilder.createTupleQuery();
        var root = query.from(DecisaoJudicial.class);
        query.multiselect(
                        root.get("orgaoJulgador"),
                        root.get("decisao"),
                        root.get("idTribunal"),
                        root.get("dataJulgamento"))
                .where(recorte.toPredicate(root, query, criteriaBuilder));
        return entityManager.createQuery(query).getResultList().stream()
                .map(tupla -> new Decisao(
                        tupla.get(0, String.class),
                        tupla.get(1, String.class),
                        tupla.get(2, Long.class),
                        tupla.get(3, LocalDate.class)))
                .toList();
    }

    private Map<ResultadoDecisao, Long> contarPorResultado(List<Decisao> decisoes) {
        Map<ResultadoDecisao, Long> porResultado = new EnumMap<>(ResultadoDecisao.class);
        Arrays.stream(ResultadoDecisao.values()).forEach(resultado -> porResultado.put(resultado, 0L));
        decisoes.forEach(decisao ->
                porResultado.merge(ResultadoDecisao.classificar(decisao.decisao()), 1L, Long::sum));
        return porResultado;
    }

    private Indicadores indicadores(List<Decisao> decisoes) {
        var porResultado = contarPorResultado(decisoes);
        long total = decisoes.size();
        long classificadas = total - porResultado.get(ResultadoDecisao.INDEFINIDO);
        long favoraveis = porResultado.get(ResultadoDecisao.PROVIDO) + porResultado.get(ResultadoDecisao.PARCIAL);
        Integer aderencia = classificadas == 0
                ? null
                : (int) Math.round(favoraveis * 100.0 / classificadas);
        return new Indicadores(total, classificadas, favoraveis, aderencia);
    }

    // Sempre as quatro categorias, na mesma ordem, para o gráfico não mudar de forma.
    private List<DistribuicaoItem> distribuicaoMerito(List<Decisao> decisoes) {
        return contarPorResultado(decisoes).entrySet().stream()
                .map(item -> new DistribuicaoItem(item.getKey().rotulo(), item.getValue()))
                .toList();
    }

    private List<DistribuicaoItem> distribuicaoCamaraTurma(List<Decisao> decisoes) {
        return ranking(decisoes, decisao -> nomeOuNaoInformado(decisao.orgaoJulgador()));
    }

    private List<DistribuicaoItem> distribuicaoTribunal(List<Decisao> decisoes) {
        Map<Long, String> siglas = new HashMap<>();
        tribunais.findAll().forEach(tribunal -> siglas.put(tribunal.getId(), tribunal.getSigla()));
        return ranking(decisoes, decisao -> nomeOuNaoInformado(siglas.get(decisao.idTribunal())));
    }

    // Ordem cronológica, com as decisões sem data de julgamento no fim.
    private List<DistribuicaoItem> evolucaoAnual(List<Decisao> decisoes) {
        Map<Integer, Long> porAno = new TreeMap<>();
        long semData = 0;
        for (Decisao decisao : decisoes) {
            if (decisao.dataJulgamento() == null) {
                semData++;
            } else {
                porAno.merge(decisao.dataJulgamento().getYear(), 1L, Long::sum);
            }
        }

        var evolucao = new ArrayList<DistribuicaoItem>();
        porAno.forEach((ano, quantidade) -> evolucao.add(new DistribuicaoItem(String.valueOf(ano), quantidade)));
        if (semData > 0) {
            evolucao.add(new DistribuicaoItem(SEM_DATA, semData));
        }
        return evolucao;
    }

    private static String nomeOuNaoInformado(String nome) {
        if (nome == null || nome.isBlank()) {
            return NAO_INFORMADO;
        }
        return nome.trim().replaceAll("\\s+", " ");
    }

    // As categorias com mais decisões aparecem nominalmente; o restante vira
    // "Outros" para o eixo do gráfico continuar legível.
    private List<DistribuicaoItem> ranking(List<Decisao> decisoes, Function<Decisao, String> categoria) {
        var ordenados = decisoes.stream()
                .collect(Collectors.groupingBy(categoria, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(item -> new DistribuicaoItem(item.getKey(), item.getValue()))
                .toList();
        if (ordenados.size() <= LIMITE_CATEGORIAS) {
            return ordenados;
        }

        var distribuicao = new ArrayList<>(ordenados.subList(0, LIMITE_CATEGORIAS - 1));
        long demais = ordenados.subList(LIMITE_CATEGORIAS - 1, ordenados.size()).stream()
                .mapToLong(DistribuicaoItem::quantidade)
                .sum();
        distribuicao.add(new DistribuicaoItem(OUTROS, demais));
        return distribuicao;
    }
}
