package forge.wikilaw.backend.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Persistencia dos vetores em SQL nativo: o tipo {@code vector} do pgvector nao e mapeado pelo JPA,
 * e os testes em H2 nao conhecem a extensao.
 */
@Repository
public class EmbeddingTrechoRepository {
    public static final int DIMENSIONS = 1024;

    private final JdbcClient jdbc;

    public EmbeddingTrechoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
}
