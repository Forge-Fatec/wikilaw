package forge.wikilaw.backend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    void prePersistPreencheDatasDeCriacaoEAtualizacao() {
        Usuario usuario = new Usuario();

        usuario.prePersist();

        assertThat(usuario.getCriadoEm()).isNotNull();
        assertThat(usuario.getAtualizadoEm()).isEqualTo(usuario.getCriadoEm());
    }

    @Test
    void preUpdateRenovaSomenteDataDeAtualizacao() {
        Usuario usuario = new Usuario();
        usuario.prePersist();
        var criacao = usuario.getCriadoEm();
        var atualizacaoInicial = usuario.getAtualizadoEm();

        usuario.preUpdate();

        assertThat(usuario.getCriadoEm()).isEqualTo(criacao);
        assertThat(usuario.getAtualizadoEm()).isAfterOrEqualTo(atualizacaoInicial);
    }
}
