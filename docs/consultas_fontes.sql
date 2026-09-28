-- Execute no banco wikilaw do Compose depois de iniciar o backend e importar.
-- Somente leitura. Não recria tabelas nem substitui migrations.
SELECT categoria, fonte, titulo, autores_ou_relator,
       resumo_ou_ementa, data_publicacao, data_original, url_original
FROM vw_documentos_pesquisa
ORDER BY categoria, fonte, id DESC
LIMIT 50;

SELECT categoria, fonte, count(*) AS quantidade
FROM vw_documentos_pesquisa
GROUP BY categoria, fonte ORDER BY categoria, fonte;

SELECT c.id_carga, f.sigla AS fonte, c.status, c.data_inicio,
       c.quantidade_recebida, c.quantidade_processada,
       c.quantidade_erro, c.mensagem_erro
FROM carga_dados c JOIN fonte_dados f USING (id_fonte)
ORDER BY c.id_carga DESC LIMIT 30;

SELECT id_precedente, numero_tema, tipo_precedente, situacao,
       questao_juridica, tese, url_original
FROM precedente WHERE ativo
ORDER BY id_precedente DESC LIMIT 20;

SELECT p.titulo, v.numero_registro, v.descricao, v.relator, v.leading_case
FROM precedente p JOIN precedente_processo v USING (id_precedente)
WHERE p.ativo
ORDER BY p.id_precedente, v.numero_registro LIMIT 50;

SELECT f.sigla, d.numero_processo, d.relator, d.orgao_julgador,
       d.data_julgamento, d.ementa, d.possui_inteiro_teor, d.url_original
FROM decisao_judicial d JOIN fonte_dados f USING (id_fonte)
WHERE d.ativo ORDER BY d.id_decisao DESC LIMIT 20;

-- Auditoria: não disponibilizar registro_bruto em um endpoint público.
SELECT r.id_registro_bruto, f.sigla, r.formato_payload,
       length(r.payload_texto) AS caracteres,
       (r.payload_jsonb IS NOT NULL) AS json_validado, r.id_carga
FROM registro_bruto r JOIN fonte_dados f USING (id_fonte)
ORDER BY r.id_registro_bruto DESC LIMIT 30;

-- Deve retornar zero linhas: nenhuma duplicidade de identidade por fonte/categoria.
SELECT categoria, fonte, identificador_externo, count(*)
FROM vw_documentos_pesquisa
GROUP BY categoria, fonte, identificador_externo
HAVING count(*) > 1;
