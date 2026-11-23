package forge.wikilaw.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Pergunta complementar sobre a analise juridica: a mesma pesquisa ativa da analise (os documentos sao lidos de
 * novo do acervo), a pergunta e as trocas anteriores da conversa. A conversa so existe no navegador; o servidor
 * nao guarda nada.
 */
public record PerguntaAnaliseRequest(
        @NotNull @Valid AnaliseJuridicaRequest analise,
        @NotBlank @Size(max = 1000) String pergunta,
        @Size(max = 20) List<@NotNull @Valid Troca> historico) {

    public PerguntaAnaliseRequest {
        if (historico == null) historico = List.of();
    }

    public record Troca(
            @NotBlank @Size(max = 1000) String pergunta,
            @NotBlank @Size(max = 8000) String resposta) {}
}
