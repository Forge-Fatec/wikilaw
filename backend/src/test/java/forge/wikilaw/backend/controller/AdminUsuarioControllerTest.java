package forge.wikilaw.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.dto.AlterarPerfilRequest;
import forge.wikilaw.backend.dto.CriarUsuarioRequest;
import forge.wikilaw.backend.dto.UsuarioResponse;
import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.security.UsuarioPrincipal;
import forge.wikilaw.backend.service.UsuarioService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUsuarioControllerTest {

    @Mock UsuarioService service;
    private AdminUsuarioController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUsuarioController(service);
    }

    @Test
    void listaUsuariosPeloService() {
        List<UsuarioResponse> esperados = List.of(
                new UsuarioResponse(1L, "Admin", PerfilUsuario.ADMIN, true, null));
        when(service.listar()).thenReturn(esperados);

        assertThat(controller.listar()).isSameAs(esperados);
    }

    @Test
    void criaUsuarioPeloService() {
        var request = new CriarUsuarioRequest("maria", "senha 123", PerfilUsuario.USER);
        var esperado = new UsuarioResponse(2L, "maria", PerfilUsuario.USER, true, null);
        when(service.criar(request)).thenReturn(esperado);

        assertThat(controller.criar(request)).isSameAs(esperado);
    }

    @Test
    void repassaIdDoAdministradorAoAlterarPerfil() {
        var request = new AlterarPerfilRequest(PerfilUsuario.ADMIN);
        var admin = principal(1L);
        var esperado = new UsuarioResponse(2L, "maria", PerfilUsuario.ADMIN, true, null);
        when(service.alterarPerfil(2L, request, 1L)).thenReturn(esperado);

        assertThat(controller.alterarPerfil(2L, request, admin)).isSameAs(esperado);
    }

    @Test
    void repassaIdDoAdministradorAoExcluir() {
        controller.excluir(2L, principal(1L));

        verify(service).excluir(2L, 1L);
    }

    private static UsuarioPrincipal principal(Long id) {
        return new UsuarioPrincipal(id, "Admin", "hash", true, List.of());
    }
}
