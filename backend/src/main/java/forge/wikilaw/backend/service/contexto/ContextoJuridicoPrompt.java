package forge.wikilaw.backend.service.contexto;

import forge.wikilaw.backend.service.contexto.ContextoJuridico.Campo;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.DocumentoContexto;

/**
 * Texto do contexto juridico enviado ao modelo: a pesquisa ativa (termo e filtros) e, em seguida, cada documento
 * delimitado e numerado pela posicao na lista de resultados, para citacao.
 */
public final class ContextoJuridicoPrompt {

    private ContextoJuridicoPrompt() {}

    public static String formatar(ContextoJuridico contexto) {
        var prompt = new StringBuilder();
        prompt.append("Pesquisa do usuário: ")
                .append(contexto.termo() == null ? "(sem termo informado)" : contexto.termo()).append("\n\n");
        if (!contexto.pesquisa().isEmpty()) {
            prompt.append("<pesquisa_ativa>\n");
            for (Campo campo : contexto.pesquisa())
                prompt.append(campo.nome()).append(": ").append(campo.valor()).append('\n');
            prompt.append("</pesquisa_ativa>\n\n");
        }
        int incluidos = contexto.documentos().size();
        if (contexto.totalResultados() > incluidos)
            prompt.append("Apenas ").append(incluidos).append(" dos ").append(contexto.totalResultados())
                    .append(" resultados da pesquisa (os mais relevantes) cabem no limite de contexto e estão abaixo. ")
                    .append("Considere essa limitação na análise.\n\n");
        prompt.append("Documentos encontrados (").append(incluidos).append("), numerados pela posição na lista ")
                .append("de resultados:\n");
        for (DocumentoContexto doc : contexto.documentos()) {
            prompt.append("\n<documento numero=\"").append(doc.numero()).append("\">\n");
            for (Campo campo : doc.campos())
                prompt.append(campo.nome()).append(": ").append(campo.valor()).append('\n');
            if (doc.truncado())
                prompt.append("(Conteúdo resumido para caber no limite de contexto.)\n");
            prompt.append("</documento>\n");
        }
        return prompt.toString();
    }
}
