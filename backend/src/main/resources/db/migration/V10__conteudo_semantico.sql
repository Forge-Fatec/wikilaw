CREATE TABLE documento_semantico (
    id_documento_semantico BIGSERIAL PRIMARY KEY,
    categoria VARCHAR(20) NOT NULL,
    id_decisao BIGINT REFERENCES decisao_judicial(id_decisao),
    id_precedente BIGINT REFERENCES precedente(id_precedente),
    id_documento_doutrinario BIGINT REFERENCES documento_doutrinario(id_documento),
    hash_conteudo VARCHAR(64),
    versao_preparacao VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    metadados JSONB NOT NULL DEFAULT '{}'::jsonb,
    mensagem_erro TEXT,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_documento_semantico_origem CHECK (
        num_nonnulls(id_decisao, id_precedente, id_documento_doutrinario) = 1
    ),
    CONSTRAINT ck_documento_semantico_categoria CHECK (
        categoria IN ('JURISPRUDENCIA', 'PRECEDENTE', 'DOUTRINA')
    ),
    CONSTRAINT ck_documento_semantico_status CHECK (
        status IN ('PENDENTE', 'PROCESSANDO', 'PRONTO', 'ERRO', 'INATIVO')
    ),
    UNIQUE (id_decisao),
    UNIQUE (id_precedente),
    UNIQUE (id_documento_doutrinario)
);

CREATE TABLE trecho_semantico (
    id_trecho_semantico BIGSERIAL PRIMARY KEY,
    id_documento_semantico BIGINT NOT NULL
        REFERENCES documento_semantico(id_documento_semantico) ON DELETE CASCADE,
    ordem INTEGER NOT NULL CHECK (ordem >= 0),
    secao VARCHAR(40) NOT NULL,
    texto TEXT NOT NULL,
    texto_para_embedding TEXT NOT NULL,
    quantidade_tokens INTEGER NOT NULL CHECK (quantidade_tokens > 0),
    hash_texto VARCHAR(64) NOT NULL,
    UNIQUE (id_documento_semantico, ordem)
);

CREATE INDEX idx_documento_semantico_status
    ON documento_semantico(status, versao_preparacao);
CREATE INDEX idx_trecho_semantico_documento
    ON trecho_semantico(id_documento_semantico, ordem);

