package forge.wikilaw.backend.service.contexto;

import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.exception.ContextoInsuficienteException;
import forge.wikilaw.backend.integration.ai.ContextoJuridicoProperties;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.Campo;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.DocumentoContexto;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva.ResultadoPesquisa;
import forge.wikilaw.backend.service.pesquisa.SelecaoResultados;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Monta o contexto juridico da IA a partir dos resultados selecionados da pesquisa, respeitando os limites
 * configurados.
 *
 * <p>O orcamento de caracteres e dividido igualmente entre os resultados: os dados de identificacao (titulo, tribunal,
 * processo, datas) sempre entram, e o conteudo (ementa, depois decisao) ocupa o que sobrar da cota de cada um.
 */
@Component
public class ContextoJuridicoBuilder {

    /** Abaixo disso, um trecho cortado nao ajuda a analise e o campo fica de fora. */
    static final int MIN_CARACTERES_TRECHO = 200;
    private static final String RETICENCIAS = "…";

    private final ContextoJuridicoProperties limites;

    public ContextoJuridicoBuilder(ContextoJuridicoProperties limites) {
        this.limites = limites;
    }

    /**
     * Cada documento recebe como numero a posicao do resultado na pesquisa, para a citacao [n] apontar para
     * o mesmo item que o usuario ve na lista.
     *
     * @param selecao resultados escolhidos para a analise e os que ficaram de fora
     * @throws ContextoInsuficienteException se nenhum resultado foi selecionado
     */
    public ContextoJuridico montar(PesquisaAtiva pesquisa, SelecaoResultados selecao) {
        List<ResultadoPesquisa> selecionados = selecao.selecionados();
        if (selecionados.isEmpty()) throw new ContextoInsuficienteException();

        int cota = limites.getMaxCaracteres() / selecionados.size();
        var contexto = new ArrayList<DocumentoContexto>(selecionados.size());
        for (ResultadoPesquisa resultado : selecionados)
            contexto.add(documento(resultado.posicao(), resultado.referencia(), resultado.documento(), cota));
        return new ContextoJuridico(limpar(pesquisa.termo()), DescricaoPesquisa.campos(pesquisa), contexto,
                pesquisa.totalResultados(),
                selecao.omitidos().stream().map(ResultadoPesquisa::referencia).toList());
    }

    private DocumentoContexto documento(int numero, DocumentoReferencia referencia, Summary doc, int cota) {
        var campos = new ArrayList<Campo>();
        boolean truncado = false;
        int usados = 0;

        // Identificacao: sempre entra, para o modelo e o usuario saberem de qual resultado se trata.
        for (Campo campo : identificacao(referencia, doc)) {
            String valor = limpar(campo.valor());
            if (valor == null) continue;
            String cortado = cortar(valor, limites.getMaxCaracteresCampo());
            truncado |= cortado.length() < valor.length();
            campos.add(new Campo(campo.nome(), cortado));
            usados += campo.nome().length() + cortado.length();
        }

        // Conteudo: ocupa o restante da cota, em ordem de importancia para a analise.
        for (Campo campo : List.of(new Campo("Ementa ou resumo", doc.resumoOuEmenta()), new Campo("Decisão", doc.decisao()))) {
            String valor = limpar(campo.valor());
            if (valor == null) continue;
            int restante = Math.min(limites.getMaxCaracteresCampo(), cota - usados - campo.nome().length());
            if (restante < Math.min(MIN_CARACTERES_TRECHO, valor.length())) {
                truncado = true;
                continue;
            }
            String cortado = cortar(valor, restante);
            truncado |= !cortado.equals(valor);
            campos.add(new Campo(campo.nome(), cortado));
            usados += campo.nome().length() + cortado.length();
        }
        return new DocumentoContexto(numero, referencia, limpar(doc.titulo()), campos, truncado);
    }

    private static List<Campo> identificacao(DocumentoReferencia referencia, Summary doc) {
        return List.of(
                new Campo("Categoria", categoria(referencia.categoria())),
                new Campo("Título", doc.titulo()),
                new Campo("Tipo", doc.tipoDocumento()),
                new Campo("Fonte", doc.fonte()),
                new Campo("Tribunal", doc.tribunal()),
                new Campo("Órgão julgador", doc.orgaoJulgador()),
                new Campo("Processo ou tema", doc.numeroProcessoOuTema()),
                new Campo("Relator ou autores", doc.autoresOuRelator()),
                new Campo("Data de julgamento", data(doc.dataJulgamento())),
                new Campo("Data de publicação", data(doc.dataPublicacao())),
                new Campo("Situação", doc.situacao()),
                new Campo("Resultado", doc.resultado()));
    }

    private static String cortar(String valor, int maximo) {
        if (valor.length() <= maximo) return valor;
        return valor.substring(0, maximo - RETICENCIAS.length()).stripTrailing() + RETICENCIAS;
    }

    /**
     * Normaliza espacos e neutraliza a tag que delimita os documentos no prompt,
     * para o texto do acervo nao conseguir fechar o proprio bloco e se passar por instrucao.
     */
    static String limpar(String valor) {
        if (valor == null) return null;
        String texto = valor.replace("</documento", "</ documento").replaceAll("\\s+", " ").strip();
        return texto.isEmpty() ? null : texto;
    }

    private static String data(LocalDate data) {
        return data == null ? null : data.toString();
    }

    private static String categoria(String categoria) {
        return switch (categoria == null ? "" : categoria) {
            case "decisoes" -> "Decisão judicial";
            case "precedentes" -> "Precedente";
            case "doutrina" -> "Doutrina";
            default -> categoria;
        };
    }
}
