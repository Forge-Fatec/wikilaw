-- SCRUM-127: índices de apoio à busca de precedentes.
--
-- A busca filtra por fonte, tribunal, situação e período de julgamento, e
-- pesquisa texto com LIKE '%termo%'. Um índice btree não atende o LIKE com
-- curinga à esquerda, daí o pg_trgm nos campos textuais consultados.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Filtros estruturados.
CREATE INDEX idx_precedente_julgamento ON precedente(data_julgamento);
CREATE INDEX idx_precedente_tribunal ON precedente(id_tribunal);
CREATE INDEX idx_precedente_fonte ON precedente(id_fonte);

-- Toda consulta de leitura restringe a ativo = true; o índice parcial mantém
-- fora do índice os registros que a fonte retirou do ar.
CREATE INDEX idx_precedente_ativo ON precedente(id_precedente) WHERE ativo;

-- Campos pesquisáveis por termo. As expressões repetem lower(...) porque é
-- assim que o Criteria API gera o predicado (lower(campo) LIKE ?).
CREATE INDEX idx_precedente_titulo_trgm
    ON precedente USING gin (lower(titulo) gin_trgm_ops);
CREATE INDEX idx_precedente_questao_trgm
    ON precedente USING gin (lower(questao_juridica) gin_trgm_ops);
CREATE INDEX idx_precedente_tese_trgm
    ON precedente USING gin (lower(tese) gin_trgm_ops);
CREATE INDEX idx_precedente_situacao_trgm
    ON precedente USING gin (lower(situacao) gin_trgm_ops);
CREATE INDEX idx_precedente_tipo_trgm
    ON precedente USING gin (lower(tipo_precedente) gin_trgm_ops);
CREATE INDEX idx_precedente_numero_tema_trgm
    ON precedente USING gin (lower(numero_tema) gin_trgm_ops);
