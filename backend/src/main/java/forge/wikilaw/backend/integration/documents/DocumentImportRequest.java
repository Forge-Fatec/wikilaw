package forge.wikilaw.backend.integration.documents;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

/** Uma página por chamada; use a continuação devolvida para coletar a próxima. */
public record DocumentImportRequest(
    @Size(max = 300) String termo,
    @Min(0) @Max(100000) Integer pagina,
    @Min(1) @Max(100) Integer tamanhoPagina,
    @Min(0) @Max(1000000) Integer offset,
    @Pattern(regexp = "espelhos-de-acordaos-[a-z-]+") String dataset,
    @Pattern(regexp = "[a-fA-F0-9-]{36}") String recursoId,
    @Pattern(regexp = "[0-9]{4}-[0-9Xx]{4}") String issn,
    @Size(max = 4000) String resumptionToken,
    @Size(max = 300) String conjunto,
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String desde,
    @Pattern(regexp = "[0-9]{4}-[0-9]{2}-[0-9]{2}") String ate
) {
    public int page() { return pagina == null ? 0 : pagina; }
    public int size() { return tamanhoPagina == null ? 10 : tamanhoPagina; }
    public int start() { return offset == null ? 0 : offset; }
    public String query() { return termo == null || termo.isBlank() ? "direito" : termo.trim(); }
    public String journal() { return issn == null ? "1808-2432" : issn; }
    public String datasetName() { return dataset == null ? "espelhos-de-acordaos-corte-especial" : dataset; }
}
