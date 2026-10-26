package forge.wikilaw.backend.dto;

import jakarta.validation.constraints.*;

public record ContextoPesquisaRequest(
        @Size(max = 300) String termo,
        @NotBlank @Size(max = 100000) String texto) {}
