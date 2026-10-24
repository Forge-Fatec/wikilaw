package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.entity.Usuario;
import java.time.OffsetDateTime;

public record UsuarioResponse(
        Long id,
        String login,
        PerfilUsuario perfil,
        boolean ativo,
        OffsetDateTime criadoEm) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getLogin(),
                usuario.getPerfil(),
                usuario.isAtivo(),
                usuario.getCriadoEm());
    }
}
