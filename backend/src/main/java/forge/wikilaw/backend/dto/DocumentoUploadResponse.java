package forge.wikilaw.backend.dto;

/** Text is temporary search context, never a record in the public document collection. */
public record DocumentoUploadResponse(String nome, String formato, long tamanhoBytes,
        int caracteres, Integer paginas, String texto) {}
