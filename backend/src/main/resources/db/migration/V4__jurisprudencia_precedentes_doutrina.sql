-- Expansão aditiva: mantém as tabelas processuais do DataJud.
INSERT INTO fonte_dados (nome, sigla, tipo_fonte, url_base, metodo_coleta, formato_principal, ativo)
VALUES
('Superior Tribunal de Justiça', 'STJ', 'JURISPRUDENCIA', 'https://dadosabertos.web.stj.jus.br', 'HTTP_DATASET', 'JSON/CSV', true),
('Jurisprudência TJDFT', 'TJDFT', 'JURISPRUDENCIA', 'https://jurisdf.tjdft.jus.br', 'HTTP_API', 'JSON', true),
('Biblioteca Digital Jurídica do STJ', 'BDJUR', 'DOUTRINA', 'https://bdjur.stj.jus.br', 'DSPACE_REST', 'JSON', true),
('Biblioteca Digital Brasileira de Teses e Dissertações', 'BDTD', 'DOUTRINA', 'https://bdtd.ibict.br', 'OAI_PMH', 'XML', true),
('Scientific Electronic Library Online', 'SCIELO', 'DOUTRINA', 'https://articlemeta.scielo.org', 'HTTP_API', 'JSON', true)
ON CONFLICT (sigla) DO NOTHING;

INSERT INTO tribunal (sigla, nome, uf, ramo_justica, esfera)
VALUES ('STJ', 'Superior Tribunal de Justiça', 'DF', 'Superior', 'Federal'),
('TJDFT', 'Tribunal de Justiça do Distrito Federal e dos Territórios', 'DF', 'Estadual', 'Distrital')
ON CONFLICT (sigla) DO NOTHING;

CREATE TABLE decisao_judicial (
    id_decisao BIGSERIAL PRIMARY KEY,
    id_fonte BIGINT NOT NULL REFERENCES fonte_dados(id_fonte),
    id_registro_bruto BIGINT NOT NULL REFERENCES registro_bruto(id_registro_bruto),
    identificador_externo VARCHAR(300) NOT NULL,
    titulo TEXT NOT NULL,
    url_original TEXT,
    data_publicacao DATE,
    data_original TEXT,
    atualizado_em TIMESTAMPTZ NOT NULL,
    metadados JSONB,
    numero_processo varchar(100),
    tipo_decisao text,
    ementa text,
    relator text,
    orgao_julgador text,
    data_julgamento date,
    decisao text,
    inteiro_teor text,
    possui_inteiro_teor boolean,
    id_processo bigint REFERENCES processo(id_processo),
    id_tribunal bigint REFERENCES tribunal(id_tribunal),
    UNIQUE (id_fonte, identificador_externo)
);
CREATE INDEX idx_decisao_judicial_publicacao ON decisao_judicial(data_publicacao);
CREATE INDEX idx_decisao_judicial_bruto ON decisao_judicial(id_registro_bruto);

CREATE TABLE precedente (
    id_precedente BIGSERIAL PRIMARY KEY,
    id_fonte BIGINT NOT NULL REFERENCES fonte_dados(id_fonte),
    id_registro_bruto BIGINT NOT NULL REFERENCES registro_bruto(id_registro_bruto),
    identificador_externo VARCHAR(300) NOT NULL,
    titulo TEXT NOT NULL,
    url_original TEXT,
    data_publicacao DATE,
    data_original TEXT,
    atualizado_em TIMESTAMPTZ NOT NULL,
    metadados JSONB,
    numero_tema text,
    tipo_precedente text,
    questao_juridica text,
    tese text,
    situacao text,
    data_julgamento date,
    id_tribunal bigint REFERENCES tribunal(id_tribunal),
    UNIQUE (id_fonte, identificador_externo)
);
CREATE INDEX idx_precedente_publicacao ON precedente(data_publicacao);
CREATE INDEX idx_precedente_bruto ON precedente(id_registro_bruto);

CREATE TABLE documento_doutrinario (
    id_documento BIGSERIAL PRIMARY KEY,
    id_fonte BIGINT NOT NULL REFERENCES fonte_dados(id_fonte),
    id_registro_bruto BIGINT NOT NULL REFERENCES registro_bruto(id_registro_bruto),
    identificador_externo VARCHAR(300) NOT NULL,
    titulo TEXT NOT NULL,
    url_original TEXT,
    data_publicacao DATE,
    data_original TEXT,
    atualizado_em TIMESTAMPTZ NOT NULL,
    metadados JSONB,
    tipo_documento text,
    resumo text,
    autores text,
    doi text,
    idioma text,
    periodico text,
    issn text,
    palavras_chave text,
    UNIQUE (id_fonte, identificador_externo)
);
CREATE INDEX idx_documento_doutrinario_publicacao ON documento_doutrinario(data_publicacao);
CREATE INDEX idx_documento_doutrinario_bruto ON documento_doutrinario(id_registro_bruto);

-- O CSV STJ fornece registro interno, não número CNJ. Não inventar um processo CNJ.
CREATE TABLE precedente_processo (
    id_vinculo BIGSERIAL PRIMARY KEY,
    id_precedente BIGINT NOT NULL REFERENCES precedente(id_precedente),
    id_registro_bruto BIGINT NOT NULL REFERENCES registro_bruto(id_registro_bruto),
    numero_registro VARCHAR(100) NOT NULL,
    descricao TEXT,
    relator TEXT,
    leading_case VARCHAR(20),
    UNIQUE (id_precedente, numero_registro)
);

-- Visão destinada à leitura no pgAdmin. O JSON bruto continua separado.
CREATE VIEW vw_documentos_pesquisa AS
SELECT 'JURISPRUDENCIA'::text AS categoria, d.id_decisao AS id, f.sigla AS fonte,
       d.identificador_externo, d.titulo, d.relator AS autores_ou_relator,
       d.ementa AS resumo_ou_ementa, d.data_publicacao, d.data_original, d.url_original,
       d.id_registro_bruto
FROM decisao_judicial d JOIN fonte_dados f ON f.id_fonte = d.id_fonte
UNION ALL
SELECT 'PRECEDENTE', p.id_precedente, f.sigla, p.identificador_externo, p.titulo,
       NULL, concat_ws(E'\n\n', p.questao_juridica, p.tese),
       p.data_publicacao, p.data_original, p.url_original, p.id_registro_bruto
FROM precedente p JOIN fonte_dados f ON f.id_fonte = p.id_fonte
UNION ALL
SELECT 'DOUTRINA', d.id_documento, f.sigla, d.identificador_externo, d.titulo,
       d.autores, d.resumo, d.data_publicacao, d.data_original, d.url_original, d.id_registro_bruto
FROM documento_doutrinario d JOIN fonte_dados f ON f.id_fonte = d.id_fonte;
