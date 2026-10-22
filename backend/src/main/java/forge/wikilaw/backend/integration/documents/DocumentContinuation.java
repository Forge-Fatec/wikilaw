package forge.wikilaw.backend.integration.documents;

/** Preserve source-specific filters while advancing its pagination contract. */
public final class DocumentContinuation {
    private DocumentContinuation() {}

    public static DocumentImportRequest next(DocumentSource source, DocumentImportRequest r,
            SourcePage.Continuacao c) {
        var next = switch (source) {
            case TJDFT, BDJUR -> new DocumentImportRequest(r.termo(), c.pagina(), r.tamanhoPagina(),
                    null, null, null, null, null, null, null, null);
            case PANGEA -> new DocumentImportRequest(r.termo(),c.pagina(),r.tamanhoPagina(),
                    null,null,null,null,null,null,null,null,r.acervoCompleto(),c.orgao(),c.tipo());
            case STJ, STJ_PRECEDENTES -> new DocumentImportRequest(null, null, r.tamanhoPagina(),
                    c.offset(), c.dataset() == null ? r.dataset() : c.dataset(), c.recursoId(), null, null, null, null, null);
            case SCIELO -> new DocumentImportRequest(null, null, r.tamanhoPagina(), c.offset(),
                    null, null, r.issn(), null, null, r.desde(), r.ate());
            case BDTD -> new DocumentImportRequest(r.termo(), null, r.tamanhoPagina(), c.offset(),
                    null, null, null, c.resumptionToken(),
                    c.resumptionToken() == null ? r.conjunto() : null,
                    c.resumptionToken() == null ? r.desde() : null,
                    c.resumptionToken() == null ? r.ate() : null);
        };
        return r.fullCollection() ? next.withFullCollection() : next;
    }
}
