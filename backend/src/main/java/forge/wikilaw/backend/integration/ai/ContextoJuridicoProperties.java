package forge.wikilaw.backend.integration.ai;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limites do contexto juridico enviado ao modelo de IA. Os resultados da pesquisa entram na ordem de relevancia
 * ate que um destes limites seja atingido; os demais ficam de fora e sao informados na resposta.
 */
@Validated
@ConfigurationProperties(prefix = "wikilaw.ai.contexto")
@Getter
@Setter
public class ContextoJuridicoProperties {

    /** Quantidade maxima de resultados da pesquisa enviados ao modelo. */
    @Min(1)
    @Max(50)
    private int maxDocumentos = 10;

    /** Orcamento total de caracteres do conteudo dos documentos, dividido igualmente entre eles. */
    @Min(1_000)
    @Max(400_000)
    private int maxCaracteres = 30_000;

    /** Tamanho maximo de cada campo de um documento (ementa, decisao etc.). */
    @Min(100)
    @Max(20_000)
    private int maxCaracteresCampo = 2_500;
}
