package forge.wikilaw.backend.exception;

/** A pesquisa nao trouxe resultados para servir de contexto a analise por IA. */
public class ContextoInsuficienteException extends RuntimeException {

    public static final String MENSAGEM = "Não existem resultados suficientes para contextualizar a análise. "
            + "Faça uma pesquisa que retorne documentos e tente novamente.";

    public ContextoInsuficienteException() {
        super(MENSAGEM);
    }
}
