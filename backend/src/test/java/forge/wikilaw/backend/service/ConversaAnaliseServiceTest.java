package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.dto.AnaliseJuridicaRequest;
import forge.wikilaw.backend.dto.PerguntaAnaliseRequest;
import forge.wikilaw.backend.dto.PerguntaAnaliseRequest.Troca;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.dto.RespostaAnaliseResponse.DocumentoCitado;
import forge.wikilaw.backend.exception.AiProviderException;
import forge.wikilaw.backend.exception.ContextoInsuficienteException;
import forge.wikilaw.backend.integration.ai.ContextoJuridicoProperties;
import forge.wikilaw.backend.integration.ai.GenerativeAiProvider;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.contexto.ContextoJuridicoBuilder;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtivaService;
import forge.wikilaw.backend.service.pesquisa.SelecaoResultadosRelevantes;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ConversaAnaliseServiceTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private final DocumentQueryService documents = mock(DocumentQueryService.class);
    private final GenerativeAiProvider ai = mock(GenerativeAiProvider.class);
    private final ContextoJuridicoProperties limites = new ContextoJuridicoProperties();
    private final ConversaAnaliseService service = new ConversaAnaliseService(new PesquisaAtivaService(documents),
            new SelecaoResultadosRelevantes(limites, new SearchTermProcessor()), ai,
            new ContextoJuridicoBuilder(limites), json,
            Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    private final AnaliseJuridicaRequest analise = new AnaliseJuridicaRequest("dano moral negativacao", List.of(
            new DocumentoReferencia("decisoes", 7L), new DocumentoReferencia("precedentes", 3L)));

    @BeforeEach
    void setUp() {
        when(documents.summariesByIds("decisoes", List.of(7L))).thenReturn(List.of(summary(7L, "decisoes", "REsp dano moral")));
        when(documents.summariesByIds("precedentes", List.of(3L))).thenReturn(List.of(summary(3L, "precedentes", "Tema 1061")));
        when(ai.model()).thenReturn("gemini-teste");
    }

    @Test
    void answersWithTheAnalysisDocumentsAndTheConversationAndMapsCitations() {
        when(ai.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"resposta":"O Tema 1061 fixa a tese [2].","documentosCitados":[2, 9, 2]}"""));

        var result = service.responder(new PerguntaAnaliseRequest(analise, "E no STJ?",
                List.of(new Troca("O dano e presumido?", "Sim, o dano e presumido [1]."))));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).generateJson(anyString(), prompt.capture(), any(JsonNode.class));
        assertThat(prompt.getValue()).containsSubsequence(
                "<documento numero=\"1\">", "REsp dano moral", "<documento numero=\"2\">", "Tema 1061",
                "<conversa>", "Usuário: O dano e presumido?", "Assistente: Sim, o dano e presumido [1].",
                "</conversa>", "<pergunta>", "E no STJ?", "</pergunta>");
        assertThat(result.resposta()).isEqualTo("O Tema 1061 fixa a tese [2].");
        assertThat(result.documentosCitados()).containsExactly(new DocumentoCitado(2, "precedentes", 3L, "Tema 1061"));
        assertThat(result.historicoResumido()).isFalse();
        assertThat(result.aviso()).contains("inteligência artificial");
        assertThat(result.modelo()).isEqualTo("gemini-teste");
    }

    @Test
    void sendsOnlyTheLatestExchangesAndShortensLongPreviousAnswers() {
        when(ai.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"resposta":"Resposta.","documentosCitados":[]}"""));
        var historico = IntStream.rangeClosed(1, 6)
                .mapToObj(n -> new Troca("pergunta " + n, n == 6 ? "x".repeat(3_000) : "resposta " + n))
                .toList();

        var result = service.responder(new PerguntaAnaliseRequest(analise, "nova pergunta", historico));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).generateJson(anyString(), prompt.capture(), any(JsonNode.class));
        assertThat(prompt.getValue())
                .doesNotContain("pergunta 1\n", "pergunta 2\n")
                .contains("pergunta 3", "pergunta 6")
                .contains("x".repeat(ConversaAnaliseService.MAX_CARACTERES_RESPOSTA_ANTERIOR) + "…")
                .doesNotContain("x".repeat(ConversaAnaliseService.MAX_CARACTERES_RESPOSTA_ANTERIOR + 1));
        assertThat(result.historicoResumido()).isTrue();
        assertThat(result.documentosCitados()).isEmpty();
    }

    @Test
    void rejectsAnEmptyAnswerFromTheModel() {
        when(ai.generateJson(anyString(), anyString(), any())).thenReturn(json.readTree("""
                {"resposta":"   ","documentosCitados":[1]}"""));

        assertThatThrownBy(() -> service.responder(new PerguntaAnaliseRequest(analise, "Pergunta?", null)))
                .isInstanceOf(AiProviderException.class);
    }

    @Test
    void doesNotCallTheModelWithoutSearchResults() {
        var semResultados = new AnaliseJuridicaRequest("dano moral", List.of());

        assertThatThrownBy(() -> service.responder(new PerguntaAnaliseRequest(semResultados, "Pergunta?", null)))
                .isInstanceOf(ContextoInsuficienteException.class);
        verify(ai, never()).generateJson(anyString(), anyString(), any());
    }

    private Summary summary(Long id, String categoria, String titulo) {
        return new Summary(id, "STJ", categoria, null, titulo, null, null, null, "Ementa de " + titulo, null, null,
                "STJ", null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
