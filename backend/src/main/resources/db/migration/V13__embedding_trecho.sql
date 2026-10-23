-- Um vetor por trecho; o cascade descarta o vetor quando a preparacao recria os trechos do documento.
CREATE TABLE embedding_trecho (
    id_trecho_semantico BIGINT PRIMARY KEY
        REFERENCES trecho_semantico(id_trecho_semantico) ON DELETE CASCADE,
    modelo VARCHAR(120) NOT NULL,
    dimensoes INTEGER NOT NULL CHECK (dimensoes = 1024),
    vetor vector(1024) NOT NULL,
    hash_texto VARCHAR(64) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_embedding_trecho_modelo ON embedding_trecho(modelo);

-- Vetores normalizados: distancia de cosseno para a busca por similaridade.
CREATE INDEX idx_embedding_trecho_vetor
    ON embedding_trecho USING hnsw (vetor vector_cosine_ops);
