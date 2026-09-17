-- Não expor registros que uma fonte posteriormente marque como retirados/sigilosos.
ALTER TABLE decisao_judicial ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE precedente ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE documento_doutrinario ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT true;

CREATE OR REPLACE VIEW vw_documentos_pesquisa AS
SELECT 'JURISPRUDENCIA'::text AS categoria, d.id_decisao AS id, f.sigla AS fonte,
       d.identificador_externo, d.titulo, d.relator AS autores_ou_relator,
       d.ementa AS resumo_ou_ementa, d.data_publicacao, d.data_original, d.url_original,
       d.id_registro_bruto
FROM decisao_judicial d JOIN fonte_dados f ON f.id_fonte = d.id_fonte
WHERE d.ativo
UNION ALL
SELECT 'PRECEDENTE', p.id_precedente, f.sigla, p.identificador_externo, p.titulo,
       NULL, concat_ws(E'\n\n', p.questao_juridica, p.tese),
       p.data_publicacao, p.data_original, p.url_original, p.id_registro_bruto
FROM precedente p JOIN fonte_dados f ON f.id_fonte = p.id_fonte
WHERE p.ativo
UNION ALL
SELECT 'DOUTRINA', d.id_documento, f.sigla, d.identificador_externo, d.titulo,
       d.autores, d.resumo, d.data_publicacao, d.data_original, d.url_original, d.id_registro_bruto
FROM documento_doutrinario d JOIN fonte_dados f ON f.id_fonte = d.id_fonte
WHERE d.ativo;
