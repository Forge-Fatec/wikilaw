package forge.wikilaw.backend.service.contexto;

import forge.wikilaw.backend.dto.AnaliseJuridicaResponse.FonteAnalise;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.DocumentoContexto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida as citacoes {@code [n]} que a IA escreve na analise contra os documentos do contexto e liga cada fonte
 * as secoes que ela sustenta.
 *
 * <p>So permanecem citacoes de documentos realmente enviados ao modelo. Um numero inexistente e retirado do
 * marcador, e um marcador sem nenhum numero valido sai do texto: a informacao fica sem referencia em vez de
 * apontar para uma fonte que nao existe. Marcadores que nao sao citacoes, como {@code [sic]}, nao mudam.
 */
public final class ReferenciasAnalise {

    /** Colchete com ate 60 caracteres, levando junto o espaco antes dele para nao sobrar espaco duplo. */
    private static final Pattern MARCADOR = Pattern.compile("(\\s*)\\[([^\\[\\]]{1,60})]");
    /** Numeros separados por virgula, ponto e virgula, "e" ou hifen (intervalo), opcionalmente com "doc." antes. */
    private static final Pattern CITACAO = Pattern.compile(
            "(?iu)\\s*(?:docs?\\.?\\s*)?\\d{1,4}(?:\\s*(?:,|;|-|–|e)\\s*(?:docs?\\.?\\s*)?\\d{1,4})*\\s*");
    private static final Pattern NUMERO_OU_INTERVALO = Pattern.compile("(\\d{1,4})(?:\\s*[-–]\\s*(\\d{1,4}))?");
    private static final int MAX_INTERVALO = 50;

    private final ContextoJuridico contexto;
    private final Map<Integer, Set<String>> secoesPorFonte = new TreeMap<>();
    private int descartadas;

    public ReferenciasAnalise(ContextoJuridico contexto) {
        this.contexto = contexto;
    }

    /** Devolve o texto so com citacoes validas e registra as fontes citadas na secao. */
    public String citar(String secao, String texto) {
        if (texto == null) return null;
        Matcher marcador = MARCADOR.matcher(texto);
        var resultado = new StringBuilder();
        while (marcador.find()) {
            String conteudo = marcador.group(2);
            String substituto;
            if (!CITACAO.matcher(conteudo).matches()) {
                substituto = marcador.group();
            } else {
                List<Integer> validos = validos(conteudo);
                validos.forEach(numero -> registrar(secao, numero));
                substituto = validos.isEmpty() ? ""
                        : marcador.group(1) + "[" + String.join(", ", validos.stream().map(String::valueOf).toList()) + "]";
            }
            marcador.appendReplacement(resultado, Matcher.quoteReplacement(substituto));
        }
        marcador.appendTail(resultado);
        String limpo = resultado.toString().strip();
        return limpo.isEmpty() ? null : limpo;
    }

    public List<String> citar(String secao, List<String> textos) {
        return textos.stream().map(texto -> citar(secao, texto)).filter(texto -> texto != null).toList();
    }

    /**
     * Registra a citacao direta de um documento (como em "documentos relevantes").
     *
     * @return {@code false} se o numero nao corresponde a um documento do contexto
     */
    public boolean registrar(String secao, int numero) {
        if (contexto.documento(numero) == null) {
            descartadas++;
            return false;
        }
        secoesPorFonte.computeIfAbsent(numero, chave -> new LinkedHashSet<>()).add(secao);
        return true;
    }

    /** Fontes citadas na analise, na ordem da pesquisa, com as secoes que cada uma sustenta. */
    public List<FonteAnalise> fontes() {
        var fontes = new ArrayList<FonteAnalise>(secoesPorFonte.size());
        secoesPorFonte.forEach((numero, secoes) -> {
            DocumentoContexto doc = contexto.documento(numero);
            FonteDocumento origem = doc.origem();
            fontes.add(new FonteAnalise(numero, doc.referencia().categoria(), doc.referencia().id(), doc.titulo(),
                    origem.fonte(), origem.tribunal(), origem.numeroProcessoOuTema(), origem.identificadorExterno(),
                    origem.url(), List.copyOf(secoes)));
        });
        return List.copyOf(fontes);
    }

    /** Quantas citacoes apontavam para documentos fora do contexto e foram retiradas. */
    public int descartadas() {
        return descartadas;
    }

    private List<Integer> validos(String conteudo) {
        var numeros = new LinkedHashSet<Integer>();
        Matcher item = NUMERO_OU_INTERVALO.matcher(conteudo);
        while (item.find()) {
            int inicio = Integer.parseInt(item.group(1));
            int fim = item.group(2) == null ? inicio : Integer.parseInt(item.group(2));
            if (fim < inicio || fim - inicio > MAX_INTERVALO) fim = inicio;
            for (int numero = inicio; numero <= fim; numero++) numeros.add(numero);
        }
        var validos = new ArrayList<Integer>();
        for (int numero : numeros) {
            if (contexto.documento(numero) != null) validos.add(numero);
            else descartadas++;
        }
        return validos;
    }
}
