package forge.wikilaw.backend.integration.documents;

/** Sinaliza retirada explícita da fonte, sem apagar a trilha de auditoria. */
public class UnavailableDocumentException extends IllegalArgumentException {
    private final String identificador;
    public UnavailableDocumentException(String identificador, String message) {
        super(message);
        this.identificador=identificador;
    }
    public String identificador() { return identificador; }
}
