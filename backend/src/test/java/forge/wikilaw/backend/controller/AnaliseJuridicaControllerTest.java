package forge.wikilaw.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.dto.AnaliseJuridicaRequest;
import forge.wikilaw.backend.dto.AnaliseJuridicaResponse;
import forge.wikilaw.backend.dto.AnaliseJuridicaResponse.FonteAnalise;
import forge.wikilaw.backend.dto.AnaliseJuridicaResponse.ResultadoUtilizado;
import forge.wikilaw.backend.exception.AiProviderException;
import forge.wikilaw.backend.exception.ContextoInsuficienteException;
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
                AnaliseJuridicaService.AVISO, "gemini-3.5-flash", OffsetDateTime.parse("2026-11-03T09:00:00-03:00"), 1,
                List.of(new ResultadoUtilizado(1, "decisoes", 7L)), List.of(), false,
                List.of(new FonteAnalise(1, "decisoes", 7L, "REsp 1", "STJ", "STJ", "REsp 1", "ext-7",
                        "https://stj.jus.br/7", List.of("Resumo")))));

        mvc.perform(post("/api/pesquisas/analises").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo").value("Resumo [1]"))
                .andExpect(jsonPath("$.geradaPorIa").value(true))
                .andExpect(jsonPath("$.aviso").value(AnaliseJuridicaService.AVISO))
                .andExpect(jsonPath("$.modelo").value("gemini-3.5-flash"))
                .andExpect(jsonPath("$.resultadosUtilizados[0].posicao").value(1))
                .andExpect(jsonPath("$.resultadosUtilizados[0].categoria").value("decisoes"))
                .andExpect(jsonPath("$.resultadosUtilizados[0].id").value(7))
                .andExpect(jsonPath("$.resultadosOmitidos").isEmpty())
                .andExpect(jsonPath("$.fontes[0].numero").value(1))
                .andExpect(jsonPath("$.fontes[0].fonte").value("STJ"))
                .andExpect(jsonPath("$.fontes[0].identificadorExterno").value("ext-7"))
                .andExpect(jsonPath("$.fontes[0].url").value("https://stj.jus.br/7"))
                .andExpect(jsonPath("$.fontes[0].secoes[0]").value("Resumo"));
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
    void reportsASearchWithoutResultsAsInsufficientContext() throws Exception {
        when(service.analisar(any())).thenThrow(new ContextoInsuficienteException());

        mvc.perform(post("/api/pesquisas/analises").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termo\":\"x\",\"resultados\":[]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value(ContextoInsuficienteException.MENSAGEM));
    }

    @Test
    void rejectsMoreResultsThanTheRequestLimit() throws Exception {
        var muitos = new StringBuilder("{\"resultados\":[");
        for (int i = 1; i <= AnaliseJuridicaRequest.MAX_RESULTADOS + 1; i++) muitos.append(i > 1 ? "," : "").append("{\"categoria\":\"decisoes\",\"id\":").append(i).append('}');
        mvc.perform(post("/api/pesquisas/analises").contentType(MediaType.APPLICATION_JSON).content(muitos + "]}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).analisar(any());
    }
}
