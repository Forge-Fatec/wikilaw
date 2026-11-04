package forge.wikilaw.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.dto.AnaliseJuridicaResponse;
import forge.wikilaw.backend.exception.AiProviderException;
import forge.wikilaw.backend.service.AnaliseJuridicaService;
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
class AnaliseJuridicaControllerTest {
    private static final String BODY = """
            {"termo":"dano moral","resultados":[{"categoria":"decisoes","id":7}]}""";

    @Autowired MockMvc mvc;
    @MockitoBean AnaliseJuridicaService service;

    @Test
    void returnsTheAnalysisIdentifiedAsAiGenerated() throws Exception {
        when(service.analisar(any())).thenReturn(new AnaliseJuridicaResponse("Resumo [1]", "Questao", "Entendimento",
                List.of("Ponto"), List.of("CDC, art. 14"), List.of(), List.of(), List.of(), true,
                AnaliseJuridicaService.AVISO, "gemini-2.5-flash", OffsetDateTime.parse("2026-11-03T09:00:00-03:00"), 1));

        mvc.perform(post("/api/pesquisas/analises").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo").value("Resumo [1]"))
                .andExpect(jsonPath("$.geradaPorIa").value(true))
                .andExpect(jsonPath("$.aviso").value(AnaliseJuridicaService.AVISO))
                .andExpect(jsonPath("$.modelo").value("gemini-2.5-flash"));
    }

    @Test
    void reportsAnUnavailableAiServiceAs503() throws Exception {
        when(service.analisar(any())).thenThrow(new AiProviderException("Gemini respondeu com HTTP 500", 500));

        mvc.perform(post("/api/pesquisas/analises").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Análise por IA indisponível"));
    }

    @Test
    void reportsTheAiRateLimitAs429() throws Exception {
        when(service.analisar(any())).thenThrow(new AiProviderException("Gemini respondeu com HTTP 429", 429));

        mvc.perform(post("/api/pesquisas/analises").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void requiresBetweenOneAndTenResults() throws Exception {
        mvc.perform(post("/api/pesquisas/analises").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termo\":\"x\",\"resultados\":[]}"))
                .andExpect(status().isBadRequest());

        var muitos = new StringBuilder("{\"resultados\":[");
        for (int i = 1; i <= 11; i++) muitos.append(i > 1 ? "," : "").append("{\"categoria\":\"decisoes\",\"id\":").append(i).append('}');
        mvc.perform(post("/api/pesquisas/analises").contentType(MediaType.APPLICATION_JSON).content(muitos + "]}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).analisar(any());
    }
}
