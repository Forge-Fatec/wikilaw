package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.entity.Usuario;
import forge.wikilaw.backend.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class InitialAdminInitializerTest {

    @Mock UsuarioRepository repository;
    @Mock PasswordEncoder passwordEncoder;

    @Test
    void naoAlteraAdministradorQueJaExiste(CapturedOutput output) {
        when(repository.findByLoginIgnoreCase("Admin")).thenReturn(Optional.of(admin("hash-forte")));
        when(passwordEncoder.matches("Admin 123", "hash-forte")).thenReturn(false);
        var initializer = new InitialAdminInitializer(repository, passwordEncoder, "Admin", "senha forte 2026");

        initializer.run(new DefaultApplicationArguments());

        verify(repository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
        assertThat(output).doesNotContain("senha padrao");
    }

    @Test
    void avisaQuandoAdministradorExistenteAindaUsaASenhaPadrao(CapturedOutput output) {
        when(repository.findByLoginIgnoreCase("Admin")).thenReturn(Optional.of(admin("hash-padrao")));
        when(passwordEncoder.matches("Admin 123", "hash-padrao")).thenReturn(true);
        var initializer = new InitialAdminInitializer(repository, passwordEncoder, "Admin", "senha forte 2026");

        initializer.run(new DefaultApplicationArguments());

        verify(repository, never()).save(any());
        assertThat(output).contains("O administrador 'Admin' ainda usa a senha padrao publica");
    }

    @Test
    void avisaQuandoCriaAdministradorComASenhaPadrao(CapturedOutput output) {
        when(repository.findByLoginIgnoreCase("Admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin 123")).thenReturn("hash-padrao");
        var initializer = new InitialAdminInitializer(repository, passwordEncoder, "Admin", "Admin 123");

        initializer.run(new DefaultApplicationArguments());

        verify(repository).save(any());
        assertThat(output).contains("Defina WIKILAW_INITIAL_ADMIN_PASSWORD");
    }

    @Test
    void criaAdministradorInicialComSenhaCodificada() {
        when(repository.findByLoginIgnoreCase("Admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha forte 2026")).thenReturn("hash-seguro");
        var initializer = new InitialAdminInitializer(repository, passwordEncoder, "  Admin  ", "senha forte 2026");

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getLogin()).isEqualTo("Admin");
        assertThat(captor.getValue().getSenhaHash()).isEqualTo("hash-seguro");
        assertThat(captor.getValue().getPerfil()).isEqualTo(PerfilUsuario.ADMIN);
        assertThat(captor.getValue().isAtivo()).isTrue();
    }

    private static Usuario admin(String senhaHash) {
        Usuario admin = new Usuario();
        admin.setLogin("Admin");
        admin.setSenhaHash(senhaHash);
        admin.setPerfil(PerfilUsuario.ADMIN);
        return admin;
    }
}
