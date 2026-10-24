package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.entity.PerfilUsuario;
import jakarta.validation.constraints.NotNull;

public record AlterarPerfilRequest(@NotNull PerfilUsuario perfil) {
}
