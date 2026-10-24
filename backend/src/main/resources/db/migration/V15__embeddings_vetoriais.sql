CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE trecho_embedding (
    id_trecho BIGINT PRIMARY KEY REFERENCES trecho_semantico(id_trecho_semantico) ON DELETE CASCADE,
    hash_texto VARCHAR(64) NOT NULL,
    modelo VARCHAR(200) NOT NULL,
    embedding vector(384) NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_trecho_embedding_cosine ON trecho_embedding USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_trecho_embedding_modelo ON trecho_embedding(modelo);
CREATE INDEX idx_trecho_busca_textual ON trecho_semantico USING gin (to_tsvector('portuguese', texto_para_embedding));

-- Avoid scanning the indexed corpus again for every small batch.
CREATE TABLE fila_embedding (
    id_trecho BIGINT PRIMARY KEY REFERENCES trecho_semantico(id_trecho_semantico) ON DELETE CASCADE
);
INSERT INTO fila_embedding SELECT t.id_trecho_semantico FROM trecho_semantico t
    JOIN documento_semantico d ON d.id_documento_semantico=t.id_documento_semantico WHERE d.status='PRONTO';
CREATE TABLE modelo_embedding (
    id INTEGER PRIMARY KEY CHECK (id=1), modelo VARCHAR(200) NOT NULL
);
CREATE FUNCTION enfileirar_trecho_embedding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO fila_embedding(id_trecho) VALUES(NEW.id_trecho_semantico) ON CONFLICT DO NOTHING;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trecho_embedding_pendente AFTER INSERT OR UPDATE OF hash_texto ON trecho_semantico
    FOR EACH ROW EXECUTE FUNCTION enfileirar_trecho_embedding();
