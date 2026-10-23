package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.entity.Usuario;
import forge.wikilaw.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class InitialAdminInitializerTest {

    @Mock UsuarioRepository repository;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void naoAlteraAdministradorQueJaExiste() {
        when(repository.existsByLoginIgnoreCase("Admin")).thenReturn(true);
        var initializer = new InitialAdminInitializer(repository, passwordEncoder, "Admin", "Admin 123");

        initializer.run(new DefaultApplicationArguments());

        verify(repository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void criaAdministradorInicialComSenhaCodificada() {
        when(repository.existsByLoginIgnoreCase("  Admin  ")).thenReturn(false);
        when(passwordEncoder.encode("Admin 123")).thenReturn("hash-seguro");
        var initializer = new InitialAdminInitializer(repository, passwordEncoder, "  Admin  ", "Admin 123");

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLogin()).isEqualTo("Admin");
        assertThat(captor.getValue().getSenhaHash()).isEqualTo("hash-seguro");
        assertThat(captor.getValue().getPerfil()).isEqualTo(PerfilUsuario.ADMIN);
        assertThat(captor.getValue().isAtivo()).isTrue();
    }
}
