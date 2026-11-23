package forge.wikilaw.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.dto.MarcoAcompanhamento;
import forge.wikilaw.backend.dto.NovidadesAcompanhamentoRequest;
import forge.wikilaw.backend.service.AcompanhamentoService;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.SemanticSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AcompanhamentoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AcompanhamentoService service;

    @Test
    void returnsTheCurrentMarco() throws Exception {
        when(service.marcoAtual()).thenReturn(new MarcoAcompanhamento(120, 45, 7));

        mvc.perform(get("/api/acompanhamentos/marco"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisoes").value(120))
                .andExpect(jsonPath("$.precedentes").value(45))
                .andExpect(jsonPath("$.doutrina").value(7));
    }

    @Test
    void returnsTheNoveltiesOfAFollowedSearch() throws Exception {
        var request = new NovidadesAcompanhamentoRequest("Desconto de emprestimo nao contratado.", null,
                List.of("decisoes"), new MarcoAcompanhamento(120, 45, 7));
        when(service.novidades(request)).thenReturn(new SemanticSearchResult("gemini-embedding-001", List.of()));

        mvc.perform(post("/api/acompanhamentos/novidades").contentType(MediaType.APPLICATION_JSON).content("""
                        {"descricao":"Desconto de emprestimo nao contratado.","categorias":["decisoes"],
                         "marco":{"decisoes":120,"precedentes":45,"doutrina":7}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("gemini-embedding-001"))
                .andExpect(jsonPath("$.resultados").isEmpty());
    }

    @Test
    void rejectsAFollowedSearchWithoutMarco() throws Exception {
        mvc.perform(post("/api/acompanhamentos/novidades").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Desconto de emprestimo nao contratado.\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).novidades(any());
    }
}
