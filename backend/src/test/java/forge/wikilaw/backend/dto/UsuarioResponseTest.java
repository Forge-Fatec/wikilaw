package forge.wikilaw.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.entity.Usuario;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UsuarioResponseTest {

    @Test
    void copiaSomenteDadosPublicosDoUsuario() {
        OffsetDateTime criadoEm = OffsetDateTime.of(2026, 10, 21, 12, 0, 0, 0, ZoneOffset.UTC);
        Usuario usuario = new Usuario();
        usuario.setId(4L);
        usuario.setLogin("maria");
        usuario.setSenhaHash("segredo-que-nao-deve-sair");
        usuario.setPerfil(PerfilUsuario.USER);
        usuario.setAtivo(true);
        usuario.setCriadoEm(criadoEm);

        UsuarioResponse response = UsuarioResponse.from(usuario);

        assertThat(response).isEqualTo(new UsuarioResponse(
                4L, "maria", PerfilUsuario.USER, true, criadoEm));
        assertThat(response.toString()).doesNotContain("segredo-que-nao-deve-sair");
    }
}
