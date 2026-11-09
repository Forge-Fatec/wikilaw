package forge.wikilaw.backend.service.pesquisa;

import forge.wikilaw.backend.integration.ai.ContextoJuridicoProperties;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva.ResultadoPesquisa;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Escolhe, entre os resultados da pesquisa ativa, os que vao para a analise, ate {@code maxDocumentos}.
 *
 * <p>Na busca semantica ou na ordenacao por relevancia a lista ja vem do mais ao menos relevante, entao entram
 * os primeiros. Ordenada por data, a lista nao diz nada sobre relevancia: os resultados que contem mais palavras
 * do termo pesquisado (no titulo, numero, ementa ou decisao) tem prioridade, e o empate fica com a posicao.
 */
@Component
public class SelecaoResultadosRelevantes {

    private final ContextoJuridicoProperties limites;
    private final SearchTermProcessor termos;

    public SelecaoResultadosRelevantes(ContextoJuridicoProperties limites, SearchTermProcessor termos) {
        this.limites = limites;
        this.termos = termos;
    }

    public SelecaoResultados selecionar(PesquisaAtiva pesquisa) {
        List<ResultadoPesquisa> resultados = pesquisa.resultados();
        int limite = limites.getMaxDocumentos();
        if (resultados.size() <= limite)
            return new SelecaoResultados(resultados, List.of());

        List<String> palavras = palavras(pesquisa);
        if (pesquisa.filtros().ordenadaPorRelevancia() || palavras.isEmpty())
            return new SelecaoResultados(resultados.subList(0, limite), resultados.subList(limite, resultados.size()));

        Map<Integer, Integer> pontos = resultados.stream()
                .collect(Collectors.toMap(ResultadoPesquisa::posicao, r -> pontuacao(r.documento(), palavras)));
        Set<Integer> escolhidos = resultados.stream()
                .sorted(Comparator.comparingInt((ResultadoPesquisa r) -> -pontos.get(r.posicao()))
                        .thenComparingInt(ResultadoPesquisa::posicao))
                .limit(limite)
                .map(ResultadoPesquisa::posicao)
                .collect(Collectors.toSet());
        Map<Boolean, List<ResultadoPesquisa>> partes = resultados.stream()
                .collect(Collectors.partitioningBy(r -> escolhidos.contains(r.posicao())));
        return new SelecaoResultados(partes.get(true), partes.get(false));
    }

    /** Palavras do termo e da consulta montada a partir do documento anexado, sem conectivos nem acentos. */
    private List<String> palavras(PesquisaAtiva pesquisa) {
        String consulta = Stream.of(pesquisa.termo(), pesquisa.filtros().consultaContextual())
                .filter(Objects::nonNull).collect(Collectors.joining(" "));
        if (consulta.isBlank()) return List.of();
        return termos.tokenize(consulta).stream().map(SelecaoResultadosRelevantes::normalizar)
                .filter(palavra -> palavra.length() > 1).distinct().toList();
    }

    /** Quantas palavras diferentes da pesquisa aparecem no resultado. */
    static int pontuacao(Summary doc, List<String> palavras) {
        String texto = normalizar(Stream.of(doc.titulo(), doc.numeroProcessoOuTema(), doc.resumoOuEmenta(),
                doc.decisao(), doc.palavrasChave()).filter(Objects::nonNull).collect(Collectors.joining(" ")));
        return (int) palavras.stream().filter(texto::contains).count();
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
