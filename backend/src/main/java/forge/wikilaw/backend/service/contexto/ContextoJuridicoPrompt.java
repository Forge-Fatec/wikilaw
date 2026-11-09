package forge.wikilaw.backend.service.contexto;

import forge.wikilaw.backend.service.contexto.ContextoJuridico.Campo;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.DocumentoContexto;

/** Texto do contexto juridico enviado ao modelo, com cada documento delimitado e numerado para citacao. */
public final class ContextoJuridicoPrompt {

    private ContextoJuridicoPrompt() {}

    public static String formatar(ContextoJuridico contexto) {
        var prompt = new StringBuilder();
        prompt.append("Pesquisa do usuário: ")
                .append(contexto.termo() == null ? "(sem termo informado)" : contexto.termo()).append("\n\n");
        int incluidos = contexto.documentos().size();
        if (contexto.totalResultados() > incluidos)
            prompt.append("Apenas ").append(incluidos).append(" dos ").append(contexto.totalResultados())
                    .append(" resultados da pesquisa (os mais relevantes) cabem no limite de contexto e estão abaixo. ")
                    .append("Considere essa limitação na análise.\n\n");
        prompt.append("Documentos encontrados (").append(incluidos).append("):\n");
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
