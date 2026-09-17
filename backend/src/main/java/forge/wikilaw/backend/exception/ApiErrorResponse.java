package forge.wikilaw.backend.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(OffsetDateTime timestamp, int status, String error, List<String> details) {
}
