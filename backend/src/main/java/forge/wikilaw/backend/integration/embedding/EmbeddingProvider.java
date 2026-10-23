package forge.wikilaw.backend.integration.embedding;

import java.util.List;

/**
 * Gera vetores semanticos para os trechos do acervo e para a descricao do caso pesquisado.
 * Os vetores retornados sempre tem {@link #dimensions()} posicoes e seguem a ordem dos textos recebidos.
 */
public interface EmbeddingProvider {

    String model();

    int dimensions();

    List<float[]> embedDocuments(List<String> texts);

    float[] embedQuery(String text);
}
