package forge.wikilaw.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.dto.RespostaAnaliseResponse;
import forge.wikilaw.backend.dto.RespostaAnaliseResponse.DocumentoCitado;
import forge.wikilaw.backend.exception.AiProviderException;
import forge.wikilaw.backend.service.ConversaAnaliseService;
import java.time.OffsetDateTime;
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
class ConversaAnaliseControllerTest {

    private static final String PERGUNTA = """
            {"analise":{"termo":"dano moral","resultados":[{"categoria":"decisoes","id":7}]},
             "pergunta":"E no STJ?",
             "historico":[{"pergunta":"O dano e presumido?","resposta":"Sim [1]."}]}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean ConversaAnaliseService service;

    @Test
    void answersAFollowUpQuestion() throws Exception {
        when(service.responder(any())).thenReturn(new RespostaAnaliseResponse("No STJ o dano e presumido [1].",
                List.of(new DocumentoCitado(1, "decisoes", 7L, "REsp dano moral")), "Aviso", "gemini-teste",
                OffsetDateTime.parse("2026-11-12T09:00:00-03:00"), false));

        mvc.perform(post("/api/pesquisas/analises/perguntas").contentType(MediaType.APPLICATION_JSON).content(PERGUNTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta").value("No STJ o dano e presumido [1]."))
                .andExpect(jsonPath("$.documentosCitados[0].posicao").value(1))
                .andExpect(jsonPath("$.documentosCitados[0].id").value(7))
                .andExpect(jsonPath("$.historicoResumido").value(false));
    }

    @Test
    void rejectsABlankQuestion() throws Exception {
        mvc.perform(post("/api/pesquisas/analises/perguntas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"analise":{"termo":"dano moral","resultados":[{"categoria":"decisoes","id":7}]},"pergunta":"  "}
                        """))
                .andExpect(status().isBadRequest());

        verify(service, never()).responder(any());
    }

    @Test
    void reportsTheAiRateLimit() throws Exception {
        when(service.responder(any())).thenThrow(new AiProviderException("limite", 429));

        mvc.perform(post("/api/pesquisas/analises/perguntas").contentType(MediaType.APPLICATION_JSON).content(PERGUNTA))
                .andExpect(status().isTooManyRequests());
    }
}
