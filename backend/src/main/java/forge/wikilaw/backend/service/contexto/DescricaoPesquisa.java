package forge.wikilaw.backend.service.contexto;

import forge.wikilaw.backend.dto.PesquisaAtivaRequest;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.Campo;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Descreve a pesquisa ativa em linguagem natural para o modelo saber de onde vieram os resultados:
 * como a busca foi feita, os filtros aplicados e quantos resultados ela tem. So entram os filtros usados.
 */
final class DescricaoPesquisa {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_CARACTERES_CONSULTA = 1_000;

    private DescricaoPesquisa() {}

    static List<Campo> campos(PesquisaAtiva pesquisa) {
        PesquisaAtivaRequest filtros = pesquisa.filtros();
        var campos = new ArrayList<Campo>();
        adicionar(campos, "Tipo de busca", modo(filtros.modo()));
        adicionar(campos, "Documento do caso anexado", filtros.documentoContexto());
        adicionar(campos, "Consulta montada a partir do documento", cortar(filtros.consultaContextual()));
        adicionar(campos, "Categorias", filtros.categorias().isEmpty() ? null
                : String.join(", ", filtros.categorias().stream().map(DescricaoPesquisa::categoria).toList()));
        adicionar(campos, "Fontes", filtros.fontes().isEmpty() ? null : String.join(", ", filtros.fontes()));
        adicionar(campos, "Tribunal", filtros.tribunal());
        adicionar(campos, "Período", periodo(filtros.dataDe(), filtros.dataAte()));
        adicionar(campos, "Resultado da decisão", filtros.resultadoDecisao());
        adicionar(campos, "Órgão julgador", filtros.orgaoJulgador());
        adicionar(campos, "Ordenação", ordenacao(filtros.ordenacao()));
        adicionar(campos, "Total de resultados", String.valueOf(pesquisa.totalResultados()));
        return campos;
    }

    private static void adicionar(List<Campo> campos, String nome, String valor) {
        String texto = ContextoJuridicoBuilder.limpar(valor);
        if (texto != null) campos.add(new Campo(nome, texto));
    }

    private static String cortar(String valor) {
        if (valor == null || valor.length() <= MAX_CARACTERES_CONSULTA) return valor;
        return valor.substring(0, MAX_CARACTERES_CONSULTA - 1) + "…";
    }

    private static String modo(String modo) {
        if (modo == null) return null;
        return "similaridade".equals(modo)
                ? "Por similaridade com o caso descrito (resultados do mais ao menos semelhante)"
                : "Textual, pelas palavras do termo";
    }

    private static String ordenacao(String ordenacao) {
        if (ordenacao == null) return null;
        return switch (ordenacao) {
            case "mais-recentes" -> "Mais recentes primeiro";
            case "mais-antigos" -> "Mais antigos primeiro";
            case "relevancia" -> "Relevância";
            default -> ordenacao;
        };
    }

    private static String periodo(LocalDate de, LocalDate ate) {
        if (de != null && ate != null) return "De " + DATA.format(de) + " a " + DATA.format(ate);
        if (de != null) return "A partir de " + DATA.format(de);
        if (ate != null) return "Até " + DATA.format(ate);
        return null;
    }

    private static String categoria(String categoria) {
        return switch (categoria) {
            case "decisoes" -> "decisões judiciais";
            case "precedentes" -> "precedentes";
            case "doutrina" -> "doutrina";
            default -> categoria;
        };
    }
}
