package forge.wikilaw.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.entity.Usuario;
import org.junit.jupiter.api.Test;

class UsuarioPrincipalTest {

    @Test
    void converteUsuarioEmUserDetailsComRoleDoPerfil() {
        Usuario usuario = new Usuario();
        usuario.setId(7L);
        usuario.setLogin("gestor");
        usuario.setSenhaHash("hash");
        usuario.setPerfil(PerfilUsuario.ADMIN);
        usuario.setAtivo(true);

        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario);

        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("gestor");
        assertThat(principal.getPassword()).isEqualTo("hash");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void preservaContaInativaComoPrincipalDesabilitado() {
        Usuario usuario = new Usuario();
        usuario.setId(8L);
        usuario.setLogin("inativo");
        usuario.setSenhaHash("hash");
        usuario.setPerfil(PerfilUsuario.USER);
        usuario.setAtivo(false);

        assertThat(UsuarioPrincipal.from(usuario).isEnabled()).isFalse();
    }
}
