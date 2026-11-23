package forge.wikilaw.backend.exception;

/** Servico de IA desligado, sem chave, fora do ar, no limite de requisicoes ou com resposta inutilizavel. */
public class AiProviderException extends RuntimeException {

    private final int statusCode;

    public AiProviderException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
