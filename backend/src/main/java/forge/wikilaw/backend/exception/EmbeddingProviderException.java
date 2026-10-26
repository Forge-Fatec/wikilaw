package forge.wikilaw.backend.exception;

public class EmbeddingProviderException extends RuntimeException {

    private final int statusCode;

    public EmbeddingProviderException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public EmbeddingProviderException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
