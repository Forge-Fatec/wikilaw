package forge.wikilaw.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.domain.Score;
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

    @Valid
    @NotNull
    private Query query = new Query();


    @Valid
    @NotNull
    private Score score = new Score();

    /** Descricao do caso pesquisado que vira o vetor de consulta. */
    @Getter
    @Setter
    public static class Query {

        /** Descricoes muito curtas nao carregam contexto suficiente para a similaridade. */
        @Min(1)
        @Max(500)
        private int minCharacters = 20;

        /** Cerca de 5 mil tokens, bem abaixo dos 32 mil aceitos pelo voyage-4. */
        @Min(100)
        @Max(100_000)
        private int maxCharacters = 20_000;

        /** Consultas repetidas reaproveitam o vetor sem nova chamada ao provedor; 0 desliga. */
        @Min(0)
        @Max(10_000)
        private int cacheSize = 200;
    }

    @Getter
    @Setter
    public static class Score {

        /** Similaridade que vale 0%. */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double minimum = 0.2;

        /** Similaridade que vale 100%. */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double maximum = 0.7;

        @AssertTrue(message = "maximum deve ser maior que minimum")
        public boolean isRangeValid() {
            return maximum > minimum;
        }
    }
}
