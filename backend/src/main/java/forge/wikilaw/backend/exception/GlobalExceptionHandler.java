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

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleStatus(org.springframework.web.server.ResponseStatusException exception) {
        return response(HttpStatus.valueOf(exception.getStatusCode().value()),
                exception.getReason() == null ? "Não foi possível concluir a solicitação" : exception.getReason(), List.of());
    }

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

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String error, List<String> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC), status.value(), error, details));
    }
}
