package forge.wikilaw.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.repository.UsuarioRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutenticacaoIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Test
    void loginAdminGerenciaUsuariosEUserNaoAcessaAdministracao() throws Exception {
        mvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isUnauthorized());

        HttpSession adminSession = login("Admin", "Admin 123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andReturn()
                .getRequest()
                .getSession(false);

        mvc.perform(post("/api/admin/usuarios")
                        .session((org.springframework.mock.web.MockHttpSession) adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"pesquisador","senha":"senha 123","perfil":"USER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("pesquisador"))
                .andExpect(jsonPath("$.perfil").value("USER"));

        HttpSession userSession = login("pesquisador", "senha 123")
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        mvc.perform(get("/api/admin/usuarios")
                        .session((org.springframework.mock.web.MockHttpSession) userSession))
                .andExpect(status().isForbidden());

        Long id = usuarios.findByLoginIgnoreCase("pesquisador").orElseThrow().getId();
        mvc.perform(patch("/api/admin/usuarios/{id}/perfil", id)
                        .session((org.springframework.mock.web.MockHttpSession) adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perfil\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMIN"));

        mvc.perform(get("/api/auth/me")
                        .session((org.springframework.mock.web.MockHttpSession) userSession))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/admin/usuarios/{id}", id)
                        .session((org.springframework.mock.web.MockHttpSession) adminSession)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejeitaSenhaIncorretaEProtegeAContaEmUso() throws Exception {
        login("Admin", "senha errada").andExpect(status().isUnauthorized());

        MvcResult login = login("Admin", "Admin 123").andExpect(status().isOk()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        Long adminId = usuarios.findByLoginIgnoreCase("Admin").orElseThrow().getId();

        mvc.perform(delete("/api/admin/usuarios/{id}", adminId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions login(String login, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"));
    }
}
