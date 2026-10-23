package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import forge.wikilaw.backend.entity.DocumentoSemantico;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia dos vetores em SQL nativo: o tipo {@code vector} do pgvector nao e mapeado pelo JPA,
 * e os testes em H2 nao conhecem a extensao.
 */
@Repository
public class EmbeddingTrechoRepository {
    public static final int DIMENSIONS = 1024;

    private final JdbcClient jdbc;
    private final SemanticSearchProperties search;

    public EmbeddingTrechoRepository(JdbcClient jdbc, SemanticSearchProperties search) {
        this.jdbc = jdbc;
        this.search = search;
    }

    public void saveAll(String model, List<TrechoEmbedding> embeddings) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (TrechoEmbedding embedding : embeddings) {
            jdbc.sql("""
                    insert into embedding_trecho
                        (id_trecho_semantico, modelo, dimensoes, vetor, hash_texto, criado_em)
                    values (:trecho, :modelo, :dimensoes, cast(:vetor as vector), :hash, :criadoEm)
                    on conflict (id_trecho_semantico) do update set
                        modelo = excluded.modelo,
                        dimensoes = excluded.dimensoes,
                        vetor = excluded.vetor,
                        hash_texto = excluded.hash_texto,
                        criado_em = excluded.criado_em
                    """)
                    .param("trecho", embedding.idTrecho())
                    .param("modelo", model)
                    .param("dimensoes", DIMENSIONS)
                    .param("vetor", toVectorLiteral(embedding.vetor()))
                    .param("hash", embedding.hashTexto())
                    .param("criadoEm", now)
                    .update();
        }
    }

    public void deleteByDocumento(Long idDocumentoSemantico) {
        jdbc.sql("""
                delete from embedding_trecho e
                using trecho_semantico t
                where t.id_trecho_semantico = e.id_trecho_semantico
                  and t.id_documento_semantico = :documento
                """)
                .param("documento", idDocumentoSemantico)
                .update();
    }

    public long countByDocumentoAndModelo(Long idDocumentoSemantico, String model) {
        return jdbc.sql("""
                select count(*) from embedding_trecho e
                join trecho_semantico t on t.id_trecho_semantico = e.id_trecho_semantico
                where t.id_documento_semantico = :documento and e.modelo = :modelo
                """)
                .param("documento", idDocumentoSemantico)
                .param("modelo", model)
                .query(Long.class)
                .single();
    }

    /**
     * Trechos mais proximos do vetor informado pela distancia de cosseno ({@code <=>}), usando o indice HNSW.
     * Considera apenas vetores do modelo atual e documentos ativos das categorias pedidas.
     */
    @Transactional(readOnly = true)
    public List<SimilarChunk> findSimilares(float[] vector, String model,
            Collection<DocumentoSemantico.Categoria> categories, int limit) {
        if (categories.isEmpty()) return List.of();
        tuneIndexScan();
        return jdbc.sql("""
                select e.id_trecho_semantico, t.id_documento_semantico, d.categoria,
                       d.id_decisao, d.id_precedente, d.id_documento_doutrinario,
                       t.secao, t.texto,
                       1 - (e.vetor <=> cast(:vetor as vector)) as similaridade
                from embedding_trecho e
                join trecho_semantico t on t.id_trecho_semantico = e.id_trecho_semantico
                join documento_semantico d on d.id_documento_semantico = t.id_documento_semantico
                where e.modelo = :modelo
                  and d.status = 'PRONTO'
                  and d.categoria in (:categorias)
                order by e.vetor <=> cast(:vetor as vector)
                limit :limite
                """)
                .param("vetor", toVectorLiteral(vector))
                .param("modelo", model)
                .param("categorias", categories.stream().map(Enum::name).toList())
                .param("limite", limit)
                .query((row, index) -> new SimilarChunk(
                        row.getLong("id_trecho_semantico"),
                        row.getLong("id_documento_semantico"),
                        DocumentoSemantico.Categoria.valueOf(row.getString("categoria")),
                        row.getObject("id_decisao", Long.class),
                        row.getObject("id_precedente", Long.class),
                        row.getObject("id_documento_doutrinario", Long.class),
                        row.getString("secao"),
                        row.getString("texto"),
                        row.getDouble("similaridade")))
                .list();
    }

    /**
     * Ajustes do HNSW valem so para a transacao atual ({@code set_config(..., true)} = {@code SET LOCAL}).
     * Sem a varredura iterativa, filtros por modelo/categoria podem devolver menos trechos que o limite.
     */
    private void tuneIndexScan() {
        jdbc.sql("select set_config('hnsw.ef_search', :efSearch, true), "
                        + "set_config('hnsw.iterative_scan', :iterativeScan, true)")
                .param("efSearch", Integer.toString(search.getEfSearch()))
                .param("iterativeScan", search.getIterativeScan().name().toLowerCase(Locale.ROOT))
                .query((row, index) -> row.getString(1))
                .single();
    }

    /** Formato textual aceito pelo pgvector: {@code [0.1,0.2,...]}. */
    public static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length != DIMENSIONS)
            throw new IllegalArgumentException("Embedding deve ter " + DIMENSIONS + " dimensoes, recebeu "
                    + (vector == null ? 0 : vector.length));
        var literal = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i]))
                throw new IllegalArgumentException("Embedding contem valor nao finito na posicao " + i);
            if (i > 0) literal.append(',');
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }

    public record TrechoEmbedding(Long idTrecho, String hashTexto, float[] vetor) {}

    /** Trecho recuperado com a similaridade de cosseno (1 = identico) e o documento de origem. */
    public record SimilarChunk(Long idTrecho, Long idDocumentoSemantico, DocumentoSemantico.Categoria categoria,
            Long idDecisao, Long idPrecedente, Long idDocumentoDoutrinario,
            String secao, String texto, double similaridade) {}
}
