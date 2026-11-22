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

    @Test
    void userNaoAcessaIntegracoesEAnonimoNaoAcessaAApi() throws Exception {
        mvc.perform(get("/api/fontes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integrations/datajud/import").with(csrf())).andExpect(status().isUnauthorized());

        var adminSession = sessao(login("Admin", "Admin 123").andExpect(status().isOk()));
        mvc.perform(post("/api/admin/usuarios")
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"leitor","senha":"senha 123","perfil":"USER"}
                                """))
                .andExpect(status().isCreated());
        var userSession = sessao(login("leitor", "senha 123").andExpect(status().isOk()));

        mvc.perform(get("/api/fontes").session(userSession)).andExpect(status().isOk());

        mvc.perform(post("/api/integrations/datajud/import").session(userSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Acesso negado"));
        mvc.perform(post("/api/integrations/documentos/{fonte}/import", "STJ").session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/integrations/tarefas/{id}", 1).session(userSession))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/integrations/tarefas/{id}/retomar", 1).session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/integrations/tarefas/{id}/cancelar", 1).session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/usuarios").session(userSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"intruso","senha":"senha 123","perfil":"ADMIN"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void recusaAlteracaoSemTokenCsrf() throws Exception {
        var adminSession = sessao(login("Admin", "Admin 123").andExpect(status().isOk()));

        mvc.perform(post("/api/admin/usuarios")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"semcsrf","senha":"senha 123","perfil":"USER"}
                                """))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.mock.web.MockHttpSession sessao(
            org.springframework.test.web.servlet.ResultActions login) {
        return (org.springframework.mock.web.MockHttpSession) login.andReturn().getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions login(String login, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"));
    }
}
