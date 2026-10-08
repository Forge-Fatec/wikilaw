package forge.wikilaw.backend.integration.documents;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DocumentContinuationTest {
    @Test
    void oaiTokenReplacesOriginalWindowAndSet() {
        var request = new DocumentImportRequest(null, null, 20, 0, null, null, null, null,
                "teses", "2026-01-01", "2026-02-01");
        var next = DocumentContinuation.next(DocumentSource.BDTD, request,
                new SourcePage.Continuacao(null, 20, "next-token", null));
        assertThat(next.resumptionToken()).isEqualTo("next-token");
        assertThat(next.desde()).isNull(); assertThat(next.ate()).isNull(); assertThat(next.conjunto()).isNull();
    }

    @Test
    void scieloKeepsDateWindowAndJournal() {
        var request = new DocumentImportRequest(null, null, 20, 0, null, null, "1808-2432", null,
                null, "2026-01-01", "2026-02-01");
        var next = DocumentContinuation.next(DocumentSource.SCIELO, request,
                new SourcePage.Continuacao(null, 20, null, null));
        assertThat(next.offset()).isEqualTo(20); assertThat(next.issn()).isEqualTo(request.issn());
        assertThat(next.desde()).isEqualTo(request.desde()); assertThat(next.ate()).isEqualTo(request.ate());
    }
}
