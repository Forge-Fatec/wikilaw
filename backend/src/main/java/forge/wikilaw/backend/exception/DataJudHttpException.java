package forge.wikilaw.backend.exception;

public class DataJudHttpException extends RuntimeException {

    private final int statusCode;

    public DataJudHttpException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public DataJudHttpException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
