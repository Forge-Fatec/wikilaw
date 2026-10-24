package forge.wikilaw.backend.exception;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentUploadException.class)
    ResponseEntity<ApiErrorResponse> handleDocumentUpload(DocumentUploadException exception) {
        return response(exception.status(), exception.getMessage(), List.of(exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleLargeUpload(MaxUploadSizeExceededException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo excede o limite de 10 MB.", List.of("Envie um PDF ou DOCX com até 10 MB."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return response(HttpStatus.BAD_REQUEST, "Requisição inválida", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "JSON inválido", List.of("Verifique os campos e valores enviados"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String reason = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return response(status, reason, List.of());
    }

    @ExceptionHandler(SemanticQueryException.class)
    ResponseEntity<ApiErrorResponse> handleSemanticQuery(SemanticQueryException exception) {
        return response(HttpStatus.BAD_REQUEST, "Consulta semântica inválida", List.of(exception.getMessage()));
    }

    /** Provedor desligado, sem chave, fora do ar ou no limite de requisições: a busca semântica fica indisponível. */
    @ExceptionHandler(EmbeddingProviderException.class)
    ResponseEntity<ApiErrorResponse> handleEmbeddingProvider(EmbeddingProviderException exception) {
        HttpStatus status = exception.getStatusCode() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
        return response(status, "Busca semântica indisponível", List.of(exception.getMessage()));
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String error, List<String> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC), status.value(), error, details));
    }
}
