package forge.wikilaw.backend.integration.documents;

public enum DocumentSource {
    STJ, STJ_PRECEDENTES, TJDFT, BDJUR, BDTD, SCIELO;
    public String sigla() { return this == STJ_PRECEDENTES ? "STJ" : name(); }
}
