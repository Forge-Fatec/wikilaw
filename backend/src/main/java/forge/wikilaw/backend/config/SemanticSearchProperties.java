package forge.wikilaw.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Recuperacao de vetores por similaridade (pgvector/HNSW). */
@Validated
@ConfigurationProperties(prefix = "wikilaw.semantic.search")
@Getter
@Setter
public class SemanticSearchProperties {

    public enum IterativeScan { OFF, STRICT_ORDER, RELAXED_ORDER }

    /** Candidatos examinados pelo HNSW por consulta; maior = mais preciso e mais lento. */
    @Min(1)
    @Max(1000)
    private int efSearch = 100;

    /** Continua a varredura do indice quando os filtros descartam vizinhos (pgvector 0.8+). */
    @NotNull
    private IterativeScan iterativeScan = IterativeScan.RELAXED_ORDER;

    /** Trechos buscados por documento pedido, ja que um documento pode ter varios trechos parecidos. */
    @Min(1)
    @Max(20)
    private int candidateMultiplier = 5;

    @Min(1)
    @Max(100)
    private int maxResults = 50;
}
