package forge.wikilaw.backend.integration.documents;

public interface DocumentAdapter {
    DocumentSource source();
    SourcePage collect(DocumentImportRequest request, AuditedFetch fetch);
    NormalizedDocument normalize(tools.jackson.databind.JsonNode record);
}
