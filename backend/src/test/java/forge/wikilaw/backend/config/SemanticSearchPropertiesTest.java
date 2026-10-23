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

    private SemanticSearchProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("wikilaw.semantic.search", SemanticSearchProperties.class);
    }
}
