package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.dto.AlterarPerfilRequest;
import forge.wikilaw.backend.dto.CriarUsuarioRequest;
import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.entity.Usuario;
import forge.wikilaw.backend.repository.UsuarioRepository;
import forge.wikilaw.backend.security.UsuarioPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock UsuarioRepository repository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SessionRegistry sessionRegistry;

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(repository, passwordEncoder, sessionRegistry);
    }

    @Test
    void listaUsuariosNaOrdemFornecidaPeloRepositorio() {
        Usuario admin = usuario(1L, "Admin", PerfilUsuario.ADMIN);
        Usuario comum = usuario(2L, "maria", PerfilUsuario.USER);
        when(repository.findAllByOrderByLoginAsc()).thenReturn(List.of(admin, comum));

        var resposta = service.listar();

        assertThat(resposta).extracting("login").containsExactly("Admin", "maria");
        assertThat(resposta).extracting("perfil").containsExactly(PerfilUsuario.ADMIN, PerfilUsuario.USER);
    }

    @Test
    void criaUsuarioComLoginNormalizadoSenhaCodificadaEContaAtiva() {
        when(repository.existsByLoginIgnoreCase("novo.usuario")).thenReturn(false);
        when(passwordEncoder.encode("senha 123")).thenReturn("hash-bcrypt");
        when(repository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario salvo = invocation.getArgument(0);
            salvo.setId(10L);
            return salvo;
        });

        var resposta = service.criar(new CriarUsuarioRequest(
                "  novo.usuario  ", "senha 123", PerfilUsuario.USER));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        Usuario salvo = captor.getValue();
        assertThat(salvo.getLogin()).isEqualTo("novo.usuario");
        assertThat(salvo.getSenhaHash()).isEqualTo("hash-bcrypt");
        assertThat(salvo.getPerfil()).isEqualTo(PerfilUsuario.USER);
        assertThat(salvo.isAtivo()).isTrue();
        assertThat(resposta.id()).isEqualTo(10L);
    }

    @Test
    void rejeitaLoginDuplicadoSemCodificarOuSalvarSenha() {
        when(repository.existsByLoginIgnoreCase("Admin")).thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> service.criar(
                new CriarUsuarioRequest("Admin", "outra senha", PerfilUsuario.USER)));

        verifyNoInteractions(passwordEncoder);
        verify(repository, never()).save(any());
    }

    @Test
    void impedeAdministradorDeAlterarAPropriaPermissao() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.alterarPerfil(
                1L, new AlterarPerfilRequest(PerfilUsuario.USER), 1L));

        verify(repository, never()).findById(any());
    }

    @Test
    void alteraPerfilEExpiraSomenteAsSessoesDoUsuarioAfetado() {
        Usuario alvo = usuario(2L, "maria", PerfilUsuario.USER);
        UsuarioPrincipal principalAlvo = UsuarioPrincipal.from(alvo);
        UsuarioPrincipal outroPrincipal = UsuarioPrincipal.from(usuario(3L, "joao", PerfilUsuario.USER));
        SessionInformation sessaoAlvo = org.mockito.Mockito.mock(SessionInformation.class);
        when(repository.findById(2L)).thenReturn(Optional.of(alvo));
        when(repository.save(alvo)).thenReturn(alvo);
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of("ignorar", principalAlvo, outroPrincipal));
        when(sessionRegistry.getAllSessions(principalAlvo, false)).thenReturn(List.of(sessaoAlvo));

        var resposta = service.alterarPerfil(
                2L, new AlterarPerfilRequest(PerfilUsuario.ADMIN), 1L);

        assertThat(resposta.perfil()).isEqualTo(PerfilUsuario.ADMIN);
        verify(sessaoAlvo).expireNow();
        verify(sessionRegistry, never()).getAllSessions(outroPrincipal, false);
    }

    @Test
    void retornaNotFoundAoAlterarUsuarioInexistente() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> service.alterarPerfil(
                99L, new AlterarPerfilRequest(PerfilUsuario.ADMIN), 1L));
    }

    @Test
    void impedeAdministradorDeExcluirAPropriaConta() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.excluir(1L, 1L));

        verify(repository, never()).delete(any());
    }

    @Test
    void excluiOutroUsuarioEEncerraSuasSessoes() {
        Usuario alvo = usuario(2L, "maria", PerfilUsuario.USER);
        UsuarioPrincipal principal = UsuarioPrincipal.from(alvo);
        SessionInformation sessao = org.mockito.Mockito.mock(SessionInformation.class);
        when(repository.findById(2L)).thenReturn(Optional.of(alvo));
        when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(principal));
        when(sessionRegistry.getAllSessions(principal, false)).thenReturn(List.of(sessao));

        service.excluir(2L, 1L);

        verify(repository).delete(alvo);
        verify(sessao).expireNow();
    }

    private static Usuario usuario(Long id, String login, PerfilUsuario perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setLogin(login);
        usuario.setSenhaHash("hash");
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return usuario;
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(status));
    }
}
