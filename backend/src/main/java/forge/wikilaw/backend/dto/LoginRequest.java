package forge.wikilaw.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 80) String login,
        @NotBlank @Size(max = 200) String senha) {
}
