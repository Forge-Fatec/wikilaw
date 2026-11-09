package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.dto.AnaliseJuridicaRequest;
import forge.wikilaw.backend.dto.AnaliseJuridicaResponse.DocumentoRelevante;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.exception.AiProviderException;
import forge.wikilaw.backend.integration.ai.ContextoJuridicoProperties;
import forge.wikilaw.backend.integration.ai.GenerativeAiProvider;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.contexto.ContextoJuridicoBuilder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AnaliseJuridicaServiceTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private final DocumentQueryService documents = mock(DocumentQueryService.class);
    private final GenerativeAiProvider ai = mock(GenerativeAiProvider.class);
    private final AnaliseJuridicaService service = new AnaliseJuridicaService(documents, ai,
            new ContextoJuridicoBuilder(new ContextoJuridicoProperties()), json,
            Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    void sendsDocumentsInSearchOrderAndMapsCitationsBackToThem() {
        when(documents.summariesByIds("decisoes", List.of(7L))).thenReturn(List.of(summary(7L, "decisoes", "REsp dano moral")));
        when(documents.summariesByIds("precedentes", List.of(3L))).thenReturn(List.of(summary(3L, "precedentes", "Tema 1061")));
        when(ai.model()).thenReturn("gemini-teste");
        when(ai.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"resumo":"Os documentos reconhecem o dano moral [1, 2].",
                 "questaoJuridica":"Negativacao indevida",
                 "entendimentoPredominante":"Dano presumido",
                 "pontosPrincipais":["Dano in re ipsa [1]", "  "],
                 "fundamentosLegais":["CDC, art. 14"],
                 "divergencias":[],
                 "documentosRelevantes":[{"referencia":2,"motivo":"Tese firmada"},{"referencia":9,"motivo":"inexistente"},
                                         {"referencia":2,"motivo":"repetido"}],
                 "limitacoes":["Apenas dois documentos"]}"""));

        var result = service.analisar(new AnaliseJuridicaRequest("dano moral negativacao", List.of(
                new DocumentoReferencia("decisoes", 7L), new DocumentoReferencia("precedentes", 3L))));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).generateJson(anyString(), prompt.capture(), any(JsonNode.class));
        assertThat(prompt.getValue()).contains("Pesquisa do usuário: dano moral negativacao")
                .containsSubsequence("<documento numero=\"1\">", "REsp dano moral", "<documento numero=\"2\">", "Tema 1061");
        assertThat(result.resumo()).isEqualTo("Os documentos reconhecem o dano moral [1, 2].");
        assertThat(result.pontosPrincipais()).containsExactly("Dano in re ipsa [1]");
        assertThat(result.divergencias()).isEmpty();
        assertThat(result.documentosRelevantes())
                .containsExactly(new DocumentoRelevante("precedentes", 3L, "Tema 1061", "Tese firmada"));
        assertThat(result.geradaPorIa()).isTrue();
        assertThat(result.aviso()).contains("inteligência artificial");
        assertThat(result.modelo()).isEqualTo("gemini-teste");
        assertThat(result.documentosAnalisados()).isEqualTo(2);
        assertThat(result.geradaEm().toInstant()).isEqualTo(Instant.parse("2026-11-03T12:00:00Z"));
    }

    @Test
    void rejectsResultsThatAreNoLongerAvailableWithoutCallingTheAi() {
        when(documents.summariesByIds(eq("decisoes"), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.analisar(new AnaliseJuridicaRequest("x",
                List.of(new DocumentoReferencia("decisoes", 1L)))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("não está mais disponível");
        verify(ai, never()).generateJson(anyString(), anyString(), any());
    }

    @Test
    void rejectsRepeatedResults() {
        var ref = new DocumentoReferencia("decisoes", 1L);
        assertThatThrownBy(() -> service.analisar(new AnaliseJuridicaRequest("x", List.of(ref, ref))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void treatsAnAnswerWithoutSummaryAsUnavailable() {
        when(documents.summariesByIds("decisoes", List.of(1L))).thenReturn(List.of(summary(1L, "decisoes", "A")));
        when(ai.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("{\"resumo\":\"  \"}"));

        assertThatThrownBy(() -> service.analisar(new AnaliseJuridicaRequest("x",
                List.of(new DocumentoReferencia("decisoes", 1L))))).isInstanceOf(AiProviderException.class);
    }

    private Summary summary(Long id, String categoria, String titulo) {
        return new Summary(id, "STJ", categoria, null, titulo, null, null, null, "Ementa de " + titulo, null, null,
                "STJ", null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
