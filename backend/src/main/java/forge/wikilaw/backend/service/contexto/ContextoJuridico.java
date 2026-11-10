package forge.wikilaw.backend.service.contexto;

import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import java.util.List;

/**
 * Contexto juridico enviado a IA: o termo pesquisado e os resultados selecionados, cada um numerado pela posicao
 * na pesquisa e ligado ao documento do acervo. O modelo cita os documentos pelo {@code numero}.
 *
 * @param termo termo pesquisado pelo usuario, ou {@code null}
 * @param pesquisa como a pesquisa ativa foi feita e os filtros aplicados, ja prontos para o prompt
 * @param documentos resultados que cabem nos limites configurados, na ordem da pesquisa
 * @param totalResultados quantos resultados a pesquisa ativa tem
 * @param omitidos resultados que ficaram de fora por excederem os limites do modelo
 */
public record ContextoJuridico(
        String termo,
        List<Campo> pesquisa,
        List<DocumentoContexto> documentos,
        int totalResultados,
        List<DocumentoReferencia> omitidos) {

    public ContextoJuridico {
        pesquisa = List.copyOf(pesquisa);
        documentos = List.copyOf(documentos);
        omitidos = List.copyOf(omitidos);
    }

    /** Documento pelo numero citado pelo modelo (a posicao na pesquisa), ou {@code null} se nao estiver no contexto. */
    public DocumentoContexto documento(int numero) {
        return documentos.stream().filter(doc -> doc.numero() == numero).findFirst().orElse(null);
    }

    public List<DocumentoReferencia> utilizados() {
        return documentos.stream().map(DocumentoContexto::referencia).toList();
    }

    public boolean truncado() {
        return !omitidos.isEmpty() || documentos.stream().anyMatch(DocumentoContexto::truncado);
    }

    /**
     * @param numero posicao do resultado na pesquisa ativa, a partir de 1
     * @param referencia categoria e id do documento no acervo
     * @param campos dados do documento, em ordem de prioridade
     * @param truncado {@code true} se algum campo foi cortado ou deixado de fora pelo limite de caracteres
     * @param origem identificacao do documento na fonte original, para citar e abrir o registro
     */
    public record DocumentoContexto(
            int numero,
            DocumentoReferencia referencia,
            String titulo,
            List<Campo> campos,
            boolean truncado,
            FonteDocumento origem) {

        public DocumentoContexto {
            campos = List.copyOf(campos);
            if (origem == null) origem = new FonteDocumento(null, null, null, null, null);
        }
    }

    public record Campo(String nome, String valor) {}
}
