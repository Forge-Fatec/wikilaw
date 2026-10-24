package forge.wikilaw.backend.service.semantic;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VectorStore {
    private final JdbcTemplate jdbc;
    // The public view excludes withdrawn documents. Prepared text and vectors
    // must match the current hash, so stale embeddings are never searchable.
    private static final String LIVE = """
        JOIN trecho_semantico t ON t.id_trecho_semantico=e.id_trecho
        JOIN documento_semantico d ON d.id_documento_semantico=t.id_documento_semantico
        WHERE d.status='PRONTO' AND e.hash_texto=t.hash_texto AND e.modelo=?
          AND EXISTS (SELECT 1 FROM vw_documentos_pesquisa v
              WHERE v.categoria=d.categoria AND v.id=coalesce(d.id_decisao,d.id_precedente,d.id_documento_doutrinario))
        """;

    public VectorStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Pending> lockPending(int limit) {
        return jdbc.query("""
            SELECT t.id_trecho_semantico,t.hash_texto,t.texto_para_embedding
            FROM fila_embedding q JOIN trecho_semantico t ON t.id_trecho_semantico=q.id_trecho
            JOIN documento_semantico d ON d.id_documento_semantico=t.id_documento_semantico
            WHERE d.status='PRONTO'
            ORDER BY q.id_trecho LIMIT ? FOR UPDATE OF q,t SKIP LOCKED
            """, (rs, row) -> new Pending(rs.getLong(1), rs.getString(2), rs.getString(3)), limit);
    }

    public void save(Pending chunk, float[] vector) {
        int saved=jdbc.update("""
            INSERT INTO trecho_embedding(id_trecho,hash_texto,modelo,embedding)
            SELECT id_trecho_semantico,hash_texto,?,CAST(? AS vector) FROM trecho_semantico
            WHERE id_trecho_semantico=? AND hash_texto=?
            ON CONFLICT(id_trecho) DO UPDATE SET hash_texto=EXCLUDED.hash_texto,
              modelo=EXCLUDED.modelo,embedding=EXCLUDED.embedding,atualizado_em=CURRENT_TIMESTAMP
            """, EmbeddingClient.MODEL, EmbeddingClient.literal(vector), chunk.id(), chunk.hash());
        if (saved>0) jdbc.update("DELETE FROM fila_embedding WHERE id_trecho=?",chunk.id());
    }

    public void prepareModel() {
        jdbc.update("INSERT INTO modelo_embedding(id,modelo) VALUES(1,?) ON CONFLICT DO NOTHING",EmbeddingClient.MODEL);
        String previous=jdbc.queryForObject("SELECT modelo FROM modelo_embedding WHERE id=1 FOR UPDATE",String.class);
        if (!EmbeddingClient.MODEL.equals(previous)) {
            jdbc.update("INSERT INTO fila_embedding SELECT t.id_trecho_semantico FROM trecho_semantico t JOIN documento_semantico d ON d.id_documento_semantico=t.id_documento_semantico WHERE d.status='PRONTO' ON CONFLICT DO NOTHING");
            jdbc.update("UPDATE modelo_embedding SET modelo=? WHERE id=1",EmbeddingClient.MODEL);
        }
    }

    public Counts counts() {
        return jdbc.queryForObject("""
            SELECT count(*) FILTER (WHERE e.id_trecho IS NOT NULL AND e.hash_texto=t.hash_texto AND e.modelo=?), count(*)
            FROM trecho_semantico t JOIN documento_semantico d ON d.id_documento_semantico=t.id_documento_semantico
            LEFT JOIN trecho_embedding e ON e.id_trecho=t.id_trecho_semantico
            WHERE d.status='PRONTO'
            """, (rs, row) -> new Counts(rs.getLong(1), rs.getLong(2) - rs.getLong(1)), EmbeddingClient.MODEL);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<Hit> nearest(float[] vector, int limit) {
        jdbc.execute("SET LOCAL hnsw.ef_search=600");
        jdbc.execute("SET LOCAL hnsw.iterative_scan='strict_order'");
        String literal = EmbeddingClient.literal(vector);
        return jdbc.query("""
            SELECT d.categoria,coalesce(d.id_decisao,d.id_precedente,d.id_documento_doutrinario),
                   left(t.texto,2000),1-(e.embedding <=> CAST(? AS vector))
            FROM trecho_embedding e
            """ + LIVE + " ORDER BY e.embedding <=> CAST(? AS vector) LIMIT ?",
                (rs, row) -> new Hit(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getDouble(4)),
                literal, EmbeddingClient.MODEL, literal, limit);
    }

    public List<Hit> lexical(String query, int limit) {
        if (query.isBlank()) return List.of();
        return jdbc.query("""
            SELECT d.categoria,coalesce(d.id_decisao,d.id_precedente,d.id_documento_doutrinario),left(t.texto,2000),0
            FROM trecho_embedding e
            """ + LIVE + """
             AND to_tsvector('portuguese',t.texto_para_embedding) @@ websearch_to_tsquery('portuguese',?)
             ORDER BY ts_rank_cd(to_tsvector('portuguese',t.texto_para_embedding),websearch_to_tsquery('portuguese',?)) DESC,
                 e.id_trecho LIMIT ?
            """, (rs, row) -> new Hit(rs.getString(1), rs.getLong(2), rs.getString(3), 0),
                EmbeddingClient.MODEL, query, query, limit);
    }

    public record Pending(long id, String hash, String text) {}
    public record Counts(long indexados, long pendentes) {}
    public record Hit(String categoria, long id, String trecho, double similaridade) {
        public String key() { return categoria + ":" + id; }
        public String apiCategory() { return switch (categoria) {
            case "JURISPRUDENCIA" -> "decisoes"; case "PRECEDENTE" -> "precedentes"; default -> "doutrina";
        }; }
    }
}
