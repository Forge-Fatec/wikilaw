package forge.wikilaw.backend.exception;

import org.springframework.http.HttpStatus;

public class DocumentUploadException extends RuntimeException {
    private final HttpStatus status;

    public DocumentUploadException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
