CREATE TABLE fonte_dados (
    id_fonte BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    sigla VARCHAR(30) NOT NULL,
    tipo_fonte VARCHAR(40) NOT NULL,
    url_base VARCHAR(500),
    metodo_coleta VARCHAR(30),
    formato_principal VARCHAR(20),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    observacao TEXT,
    CONSTRAINT uq_fonte_dados_sigla UNIQUE (sigla)
);

CREATE TABLE carga_dados (
    id_carga BIGSERIAL PRIMARY KEY,
    id_fonte BIGINT NOT NULL REFERENCES fonte_dados (id_fonte),
    data_inicio TIMESTAMPTZ NOT NULL,
    data_fim TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL,
    quantidade_recebida INTEGER NOT NULL DEFAULT 0,
    quantidade_processada INTEGER NOT NULL DEFAULT 0,
    quantidade_erro INTEGER NOT NULL DEFAULT 0,
    mensagem_erro TEXT
);

CREATE TABLE registro_bruto (
    id_registro_bruto BIGSERIAL PRIMARY KEY,
    id_fonte BIGINT NOT NULL REFERENCES fonte_dados (id_fonte),
    id_carga BIGINT REFERENCES carga_dados (id_carga),
    identificador_externo VARCHAR(300),
    formato_payload VARCHAR(20) NOT NULL,
    data_coleta TIMESTAMPTZ NOT NULL,
    hash_conteudo VARCHAR(64) NOT NULL,
    payload_texto TEXT,
    payload_jsonb JSONB
);

CREATE INDEX ix_registro_bruto_fonte_hash
    ON registro_bruto (id_fonte, hash_conteudo);
CREATE INDEX ix_registro_bruto_carga
    ON registro_bruto (id_carga);
CREATE INDEX ix_registro_bruto_payload_jsonb
    ON registro_bruto USING GIN (payload_jsonb);

CREATE TABLE tribunal (
    id_tribunal BIGSERIAL PRIMARY KEY,
    sigla VARCHAR(20) NOT NULL,
    nome VARCHAR(250) NOT NULL,
    uf CHAR(2),
    ramo_justica VARCHAR(80),
    esfera VARCHAR(40),
    CONSTRAINT uq_tribunal_sigla UNIQUE (sigla)
);

CREATE TABLE orgao_julgador (
    id_orgao_julgador BIGSERIAL PRIMARY KEY,
    id_tribunal BIGINT NOT NULL REFERENCES tribunal (id_tribunal),
    codigo_externo VARCHAR(50) NOT NULL,
    nome VARCHAR(250) NOT NULL,
    codigo_municipio_ibge BIGINT,
    CONSTRAINT uq_orgao_tribunal_codigo UNIQUE (id_tribunal, codigo_externo)
);

CREATE TABLE classe_processual (
    id_classe_processual BIGSERIAL PRIMARY KEY,
    codigo_cnj BIGINT NOT NULL,
    nome VARCHAR(250) NOT NULL,
    CONSTRAINT uq_classe_codigo_cnj UNIQUE (codigo_cnj)
);

CREATE TABLE assunto_processual (
    id_assunto_processual BIGSERIAL PRIMARY KEY,
    codigo_cnj BIGINT NOT NULL,
    nome VARCHAR(500) NOT NULL,
    CONSTRAINT uq_assunto_codigo_cnj UNIQUE (codigo_cnj)
);

CREATE TABLE processo (
    id_processo BIGSERIAL PRIMARY KEY,
    numero_cnj VARCHAR(30) NOT NULL,
    CONSTRAINT uq_processo_numero_cnj UNIQUE (numero_cnj)
);

CREATE TABLE processo_instancia (
    id_processo_instancia BIGSERIAL PRIMARY KEY,
    id_processo BIGINT NOT NULL REFERENCES processo (id_processo),
    id_fonte BIGINT NOT NULL REFERENCES fonte_dados (id_fonte),
    id_registro_bruto BIGINT REFERENCES registro_bruto (id_registro_bruto),
    id_tribunal BIGINT NOT NULL REFERENCES tribunal (id_tribunal),
    id_orgao_julgador_atual BIGINT REFERENCES orgao_julgador (id_orgao_julgador),
    id_classe_processual BIGINT REFERENCES classe_processual (id_classe_processual),
    identificador_externo VARCHAR(300) NOT NULL,
    grau VARCHAR(20),
    data_ajuizamento TIMESTAMPTZ,
    nivel_sigilo INTEGER,
    codigo_sistema VARCHAR(50),
    nome_sistema VARCHAR(100),
    formato_processo VARCHAR(50),
    data_ultima_atualizacao_fonte TIMESTAMPTZ,
    CONSTRAINT uq_instancia_fonte_identificador UNIQUE (id_fonte, identificador_externo)
);

CREATE INDEX ix_processo_instancia_processo
    ON processo_instancia (id_processo);

CREATE TABLE processo_assunto (
    id_processo_instancia BIGINT NOT NULL REFERENCES processo_instancia (id_processo_instancia) ON DELETE CASCADE,
    id_assunto_processual BIGINT NOT NULL REFERENCES assunto_processual (id_assunto_processual),
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id_processo_instancia, id_assunto_processual)
);

CREATE TABLE movimento_processual (
    id_movimento BIGSERIAL PRIMARY KEY,
    id_processo_instancia BIGINT NOT NULL REFERENCES processo_instancia (id_processo_instancia) ON DELETE CASCADE,
    id_orgao_julgador BIGINT REFERENCES orgao_julgador (id_orgao_julgador),
    codigo_cnj BIGINT,
    nome VARCHAR(500),
    data_hora TIMESTAMPTZ,
    complementos_jsonb JSONB
);

CREATE INDEX ix_movimento_instancia_data
    ON movimento_processual (id_processo_instancia, data_hora);

COMMENT ON COLUMN movimento_processual.nome IS
    'Nullable because current DataJud responses can omit the movement name even when a code is present.';
