package forge.wikilaw.backend.dto;

import jakarta.validation.constraints.*;
import java.util.List;

/** Bounded candidate set from similarity search; no uploaded text is needed for aggregations. */
public record DashboardResultadosRequest(@NotNull @Size(max=200) List<@NotNull @Positive Long> idsDecisoes,
        @Size(max=40) String resultado, @Size(max=200) String orgaoJulgador) {}
