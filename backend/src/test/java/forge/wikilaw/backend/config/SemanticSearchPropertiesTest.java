package forge.wikilaw.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SemanticSearchPropertiesTest {

    @Test
    void usesPrecisionOrientedDefaults() {
        SemanticSearchProperties properties = bind(Map.of());

        assertThat(properties.getEfSearch()).isEqualTo(100);
        assertThat(properties.getIterativeScan()).isEqualTo(SemanticSearchProperties.IterativeScan.RELAXED_ORDER);
        assertThat(properties.getCandidateMultiplier()).isEqualTo(5);
        assertThat(properties.getMaxResults()).isEqualTo(50);
    }

    @Test
    void bindsLowercaseIterativeScanFromEnvironment() {
        SemanticSearchProperties properties = bind(Map.of(
                "wikilaw.semantic.search.ef-search", "200",
                "wikilaw.semantic.search.iterative-scan", "strict_order",
                "wikilaw.semantic.search.candidate-multiplier", "3",
                "wikilaw.semantic.search.max-results", "20"));

        assertThat(properties.getEfSearch()).isEqualTo(200);
        assertThat(properties.getIterativeScan()).isEqualTo(SemanticSearchProperties.IterativeScan.STRICT_ORDER);
        assertThat(properties.getCandidateMultiplier()).isEqualTo(3);
        assertThat(properties.getMaxResults()).isEqualTo(20);
    }

    @Test
    void bindsSearchedCaseLimits() {
        SemanticSearchProperties defaults = bind(Map.of());
        assertThat(defaults.getQuery().getMinCharacters()).isEqualTo(20);
        assertThat(defaults.getQuery().getMaxCharacters()).isEqualTo(20_000);
        assertThat(defaults.getQuery().getCacheSize()).isEqualTo(200);

        SemanticSearchProperties configured = bind(Map.of(
                "wikilaw.semantic.search.query.min-characters", "10",
                "wikilaw.semantic.search.query.max-characters", "5000",
                "wikilaw.semantic.search.query.cache-size", "0"));
        assertThat(configured.getQuery().getMinCharacters()).isEqualTo(10);
        assertThat(configured.getQuery().getMaxCharacters()).isEqualTo(5000);
        assertThat(configured.getQuery().getCacheSize()).isZero();
    }

    private SemanticSearchProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("wikilaw.semantic.search", SemanticSearchProperties.class);
    }
}
