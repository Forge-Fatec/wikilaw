package forge.wikilaw.backend.exception;

public class DataJudParseException extends RuntimeException {
    public DataJudParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataJudParseException(String message) {
        super(message);
    }
}
