CREATE TABLE usuario (
    id_usuario BIGSERIAL PRIMARY KEY,
    login VARCHAR(80) NOT NULL,
    senha_hash VARCHAR(100) NOT NULL,
    perfil VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMIN', 'USER'))
);

CREATE UNIQUE INDEX uq_usuario_login_case_insensitive
    ON usuario (LOWER(login));
