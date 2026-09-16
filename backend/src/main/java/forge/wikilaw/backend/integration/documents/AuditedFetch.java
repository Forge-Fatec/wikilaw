package forge.wikilaw.backend.integration.documents;

public interface AuditedFetch {
    Payload request(String url, String body, String formato);
    default Payload get(String url, String formato) { return request(url, null, formato); }
    record Payload(String texto, Long registroBrutoId) {}
}
