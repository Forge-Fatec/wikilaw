ALTER TABLE documento_semantico
    ADD COLUMN status_embedding VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    ADD COLUMN modelo_embedding VARCHAR(120),
    ADD COLUMN tentativas_embedding INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN erro_embedding TEXT,
    ADD COLUMN embedding_atualizado_em TIMESTAMPTZ,
    ADD CONSTRAINT ck_documento_semantico_status_embedding CHECK (
        status_embedding IN ('PENDENTE', 'PROCESSANDO', 'PRONTO', 'ERRO')
    ),
    ADD CONSTRAINT ck_documento_semantico_tentativas_embedding CHECK (tentativas_embedding >= 0);

CREATE INDEX idx_documento_semantico_status_embedding
    ON documento_semantico(status_embedding, modelo_embedding)
    WHERE status = 'PRONTO';
