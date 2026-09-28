INSERT INTO fonte_dados
    (nome, sigla, tipo_fonte, url_base, metodo_coleta, formato_principal, ativo, observacao)
VALUES
    ('DataJud - Conselho Nacional de Justica', 'DATAJUD', 'PROCESSUAL',
     'https://api-publica.datajud.cnj.jus.br', 'API', 'JSON', TRUE,
     'Metadados processuais publicos: capa e movimentos; nao e fonte de inteiro teor.')
ON CONFLICT (sigla) DO NOTHING;

INSERT INTO tribunal (sigla, nome, uf, ramo_justica, esfera)
VALUES
    ('TJSP', 'Tribunal de Justica de Sao Paulo', 'SP', 'JUSTICA_ESTADUAL', 'ESTADUAL'),
    ('TJRJ', 'Tribunal de Justica do Rio de Janeiro', 'RJ', 'JUSTICA_ESTADUAL', 'ESTADUAL'),
    ('TJMG', 'Tribunal de Justica de Minas Gerais', 'MG', 'JUSTICA_ESTADUAL', 'ESTADUAL')
ON CONFLICT (sigla) DO NOTHING;
