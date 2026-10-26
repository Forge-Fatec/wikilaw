package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.entity.PerfilUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriarUsuarioRequest(
        @NotBlank
        @Size(min = 3, max = 80)
        @Pattern(regexp = "^[\\p{L}\\p{N}._-]+$", message = "use apenas letras, numeros, ponto, hifen ou sublinhado")
        String login,
        @NotBlank @Size(min = 8, max = 72) String senha,
        @NotNull PerfilUsuario perfil) {
}
