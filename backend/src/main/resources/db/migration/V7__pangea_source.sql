INSERT INTO fonte_dados (nome, sigla, tipo_fonte, url_base, metodo_coleta, formato_principal, ativo)
VALUES ('Pangea / Banco Nacional de Precedentes', 'PANGEA', 'JURISPRUDENCIA',
        'https://pangeabnp.pdpj.jus.br/api/v1', 'HTTP_API', 'JSON', true)
ON CONFLICT (sigla) DO NOTHING;

-- A origem de um precedente Pangea não é necessariamente o STJ.
ALTER TABLE precedente ADD COLUMN tribunal_origem VARCHAR(20);
UPDATE precedente p SET tribunal_origem = t.sigla FROM tribunal t WHERE t.id_tribunal = p.id_tribunal;
