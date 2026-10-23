package forge.wikilaw.backend.exception;

/** Descricao do caso pesquisado que nao pode virar consulta semantica (vazia, curta ou longa demais). */
public class SemanticQueryException extends RuntimeException {

    public SemanticQueryException(String message) {
        super(message);
    }
}
