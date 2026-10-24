package forge.wikilaw.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.dto.LoginRequest;
import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.entity.Usuario;
import forge.wikilaw.backend.repository.UsuarioRepository;
import forge.wikilaw.backend.security.UsuarioPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AutenticacaoControllerTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock UsuarioRepository repository;
    @Mock SessionRegistry sessionRegistry;
    @Mock CsrfToken csrfToken;

    private AutenticacaoController controller;

    @BeforeEach
    void setUp() {
        controller = new AutenticacaoController(authenticationManager, repository, sessionRegistry);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retornaTokenCsrfESeuHeader() {
        when(csrfToken.getToken()).thenReturn("token-123");
        when(csrfToken.getHeaderName()).thenReturn("X-XSRF-TOKEN");

        assertThat(controller.csrf(csrfToken))
                .containsEntry("token", "token-123")
                .containsEntry("headerName", "X-XSRF-TOKEN");
    }

    @Test
    void autenticaLoginNormalizadoPersisteContextoERegistraSessao() {
        Usuario usuario = usuario(1L, "Admin", PerfilUsuario.ADMIN);
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario);
        Authentication autenticado = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(autenticado);
        when(repository.findById(1L)).thenReturn(Optional.of(usuario));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var resultado = controller.login(
                new LoginRequest("  Admin  ", "Admin 123"), request, response);

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Admin");
        assertThat(captor.getValue().getCredentials()).isEqualTo("Admin 123");
        assertThat(resultado.login()).isEqualTo("Admin");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(autenticado);
        String sessionId = request.getSession(false).getId();
        verify(sessionRegistry).removeSessionInformation(sessionId);
        verify(sessionRegistry).registerNewSession(sessionId, principal);
    }

    @Test
    void converteFalhaDeAutenticacaoEmUnauthorized() {
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(new BadCredentialsException("invalido"));

        assertThatThrownBy(() -> controller.login(
                new LoginRequest("Admin", "errada"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason()).isEqualTo("Login ou senha invalidos");
                });
    }

    @Test
    void retornaUsuarioDaSessaoAtual() {
        Usuario usuario = usuario(2L, "maria", PerfilUsuario.USER);
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(repository.findById(2L)).thenReturn(Optional.of(usuario));

        var resposta = controller.me(authentication);

        assertThat(resposta.id()).isEqualTo(2L);
        assertThat(resposta.perfil()).isEqualTo(PerfilUsuario.USER);
    }

    @Test
    void retornaUnauthorizedQuandoUsuarioDaSessaoNaoExisteMais() {
        UsuarioPrincipal principal = new UsuarioPrincipal(99L, "removido", "hash", true, List.of());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.me(authentication))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void logoutRemoveRegistroEInvalidaSessaoHttp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        var session = request.getSession();
        String sessionId = session.getId();
        MockHttpServletResponse response = new MockHttpServletResponse();
        UsuarioPrincipal principal = new UsuarioPrincipal(1L, "Admin", "hash", true, List.of());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        controller.logout(authentication, request, response);

        verify(sessionRegistry).removeSessionInformation(sessionId);
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
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
}
