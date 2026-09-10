-- Created by Redgate Data Modeler (https://datamodeler.redgate-platform.com)
-- Last modification date: 2026-09-06 04:03:23.108

-- tables
-- Table: assunto_processual
CREATE TABLE assunto_processual (
    id_assunto_processual bigserial  NOT NULL,
    codigo_cnj bigint  NULL,
    nome varchar(500)  NOT NULL,
    CONSTRAINT uq_assunto_codigo_cnj UNIQUE (codigo_cnj) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT assunto_processual_pk PRIMARY KEY (id_assunto_processual)
);

-- Table: carga_dados
CREATE TABLE carga_dados (
    id_carga bigserial  NOT NULL,
    id_fonte bigint  NOT NULL,
    data_inicio timestamp  NOT NULL,
    data_fim timestamp  NULL,
    status varchar(30)  NOT NULL,
    quantidade_recebida integer  NULL,
    quantidade_processada integer  NULL,
    quantidade_erro integer  NULL,
    mensagem_erro text  NULL,
    CONSTRAINT carga_dados_pk PRIMARY KEY (id_carga)
);

-- Table: classe_processual
CREATE TABLE classe_processual (
    id_classe_processual bigserial  NOT NULL,
    codigo_cnj bigint  NULL,
    nome varchar(250)  NOT NULL,
    CONSTRAINT uq_classe_codigo_cnj UNIQUE (codigo_cnj) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT classe_processual_pk PRIMARY KEY (id_classe_processual)
);

-- Table: decisao_judicial
CREATE TABLE decisao_judicial (
    id_decisao bigserial  NOT NULL,
    id_fonte bigint  NOT NULL,
    id_registro_bruto bigint  NULL,
    id_processo bigint  NULL,
    id_tribunal bigint  NOT NULL,
    id_orgao_julgador bigint  NULL,
    id_classe_processual bigint  NULL,
    identificador_externo varchar(300)  NULL,
    uuid_externo varchar(100)  NULL,
    numero_processo_original varchar(50)  NULL,
    base_origem varchar(100)  NULL,
    subbase_origem varchar(100)  NULL,
    tipo_decisao varchar(80)  NULL,
    versao varchar(30)  NULL,
    data_julgamento date  NULL,
    data_publicacao timestamp  NULL,
    ementa text  NULL,
    inteiro_teor text  NULL,
    possui_inteiro_teor boolean  NULL,
    marcadores_jsonb jsonb  NULL,
    url_original varchar(1000)  NULL,
    CONSTRAINT uq_decisao_fonte_identificador UNIQUE (id_fonte, identificador_externo) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT decisao_judicial_pk PRIMARY KEY (id_decisao)
);

-- Table: decisao_pessoa
CREATE TABLE decisao_pessoa (
    id_decisao bigint  NOT NULL,
    id_pessoa bigint  NOT NULL,
    papel varchar(40)  NOT NULL,
    CONSTRAINT decisao_pessoa_pk PRIMARY KEY (id_decisao,id_pessoa,papel)
);

-- Table: documento_doutrinario
CREATE TABLE documento_doutrinario (
    id_documento bigserial  NOT NULL,
    id_fonte bigint  NOT NULL,
    id_registro_bruto bigint  NULL,
    id_instituicao bigint  NULL,
    id_periodico bigint  NULL,
    identificador_externo varchar(300)  NULL,
    oai_identifier varchar(300)  NULL,
    tipo_documento varchar(80)  NOT NULL,
    titulo varchar(1000)  NOT NULL,
    subtitulo varchar(1000)  NULL,
    resumo text  NULL,
    idioma varchar(20)  NULL,
    ano_publicacao integer  NULL,
    data_publicacao date  NULL,
    ano_defesa integer  NULL,
    doi varchar(200)  NULL,
    isbn varchar(50)  NULL,
    volume varchar(50)  NULL,
    numero_edicao varchar(50)  NULL,
    elocation varchar(100)  NULL,
    paginas varchar(100)  NULL,
    programa_pos_graduacao varchar(500)  NULL,
    departamento varchar(500)  NULL,
    colecao varchar(500)  NULL,
    fonte_bibliografica text  NULL,
    notas text  NULL,
    tipo_acesso varchar(80)  NULL,
    acesso_aberto boolean  NULL,
    licenca varchar(200)  NULL,
    url_original varchar(1000)  NULL,
    url_texto_completo varchar(1000)  NULL,
    CONSTRAINT uq_documento_fonte_identificador UNIQUE (id_fonte, identificador_externo) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT documento_doutrinario_pk PRIMARY KEY (id_documento)
);

-- Table: documento_palavra_chave
CREATE TABLE documento_palavra_chave (
    id_documento bigint  NOT NULL,
    id_palavra_chave bigint  NOT NULL,
    CONSTRAINT documento_palavra_chave_pk PRIMARY KEY (id_documento,id_palavra_chave)
);

-- Table: documento_pessoa
CREATE TABLE documento_pessoa (
    id_documento bigint  NOT NULL,
    id_pessoa bigint  NOT NULL,
    papel varchar(50)  NOT NULL,
    ordem integer  NULL,
    CONSTRAINT documento_pessoa_pk PRIMARY KEY (id_documento,id_pessoa,papel)
);

-- Table: fonte_dados
CREATE TABLE fonte_dados (
    id_fonte bigserial  NOT NULL,
    nome varchar(100)  NOT NULL,
    sigla varchar(30)  NOT NULL,
    tipo_fonte varchar(40)  NOT NULL,
    url_base varchar(500)  NULL,
    metodo_coleta varchar(30)  NULL,
    formato_principal varchar(20)  NULL,
    ativo boolean  NOT NULL DEFAULT true,
    observacao text  NULL,
    CONSTRAINT uq_fonte_dados_sigla UNIQUE (sigla) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT fonte_dados_pk PRIMARY KEY (id_fonte)
);

-- Table: instituicao
CREATE TABLE instituicao (
    id_instituicao bigserial  NOT NULL,
    nome varchar(400)  NOT NULL,
    sigla varchar(80)  NULL,
    tipo_instituicao varchar(80)  NULL,
    pais varchar(100)  NULL,
    uf char(2)  NULL,
    cidade varchar(150)  NULL,
    CONSTRAINT instituicao_pk PRIMARY KEY (id_instituicao)
);

-- Table: movimento_processual
CREATE TABLE movimento_processual (
    id_movimento bigserial  NOT NULL,
    id_processo_instancia bigint  NOT NULL,
    id_orgao_julgador bigint  NULL,
    codigo_cnj bigint  NULL,
    nome varchar(500)  NOT NULL,
    data_hora timestamp  NULL,
    complementos_jsonb jsonb  NULL,
    CONSTRAINT movimento_processual_pk PRIMARY KEY (id_movimento)
);

-- Table: orgao_julgador
CREATE TABLE orgao_julgador (
    id_orgao_julgador bigserial  NOT NULL,
    id_tribunal bigint  NOT NULL,
    codigo_externo varchar(50)  NULL,
    nome varchar(250)  NOT NULL,
    codigo_municipio_ibge bigint  NULL,
    CONSTRAINT uq_orgao_tribunal_codigo UNIQUE (id_tribunal, codigo_externo) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT orgao_julgador_pk PRIMARY KEY (id_orgao_julgador)
);

-- Table: palavra_chave
CREATE TABLE palavra_chave (
    id_palavra_chave bigserial  NOT NULL,
    termo varchar(500)  NOT NULL,
    idioma varchar(20)  NULL,
    CONSTRAINT uq_palavra_chave_termo_idioma UNIQUE (termo, idioma) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT palavra_chave_pk PRIMARY KEY (id_palavra_chave)
);

-- Table: periodico
CREATE TABLE periodico (
    id_periodico bigserial  NOT NULL,
    titulo varchar(400)  NOT NULL,
    titulo_abreviado varchar(200)  NULL,
    issn varchar(30)  NULL,
    eissn varchar(30)  NULL,
    editora varchar(300)  NULL,
    CONSTRAINT periodico_pk PRIMARY KEY (id_periodico)
);

-- Table: pessoa
CREATE TABLE pessoa (
    id_pessoa bigserial  NOT NULL,
    nome varchar(300)  NOT NULL,
    orcid varchar(30)  NULL,
    pais varchar(100)  NULL,
    CONSTRAINT uq_pessoa_orcid UNIQUE (orcid) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT pessoa_pk PRIMARY KEY (id_pessoa)
);

-- Table: precedente
CREATE TABLE precedente (
    id_precedente bigserial  NOT NULL,
    id_fonte bigint  NOT NULL,
    id_registro_bruto bigint  NULL,
    id_tribunal bigint  NULL,
    sequencial_externo varchar(100)  NULL,
    numero_tema varchar(50)  NULL,
    tipo_precedente varchar(100)  NULL,
    titulo varchar(500)  NULL,
    questao_juridica text  NULL,
    tese text  NULL,
    situacao varchar(100)  NULL,
    data_afetacao date  NULL,
    data_julgamento date  NULL,
    data_publicacao date  NULL,
    observacao text  NULL,
    url_original varchar(1000)  NULL,
    CONSTRAINT uq_precedente_fonte_sequencial UNIQUE (id_fonte, sequencial_externo) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT precedente_pk PRIMARY KEY (id_precedente)
);

-- Table: precedente_processo
CREATE TABLE precedente_processo (
    id_precedente bigint  NOT NULL,
    id_processo bigint  NOT NULL,
    tipo_vinculo varchar(80)  NULL,
    CONSTRAINT precedente_processo_pk PRIMARY KEY (id_precedente,id_processo)
);

-- Table: processo
CREATE TABLE processo (
    id_processo bigserial  NOT NULL,
    numero_cnj varchar(30)  NOT NULL,
    CONSTRAINT uq_processo_numero_cnj UNIQUE (numero_cnj) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT processo_pk PRIMARY KEY (id_processo)
);

-- Table: processo_assunto
CREATE TABLE processo_assunto (
    id_processo_instancia bigint  NOT NULL,
    id_assunto_processual bigint  NOT NULL,
    principal boolean  NOT NULL DEFAULT false,
    CONSTRAINT processo_assunto_pk PRIMARY KEY (id_processo_instancia,id_assunto_processual)
);

-- Table: processo_instancia
CREATE TABLE processo_instancia (
    id_processo_instancia bigserial  NOT NULL,
    id_processo bigint  NOT NULL,
    id_fonte bigint  NOT NULL,
    id_registro_bruto bigint  NULL,
    id_tribunal bigint  NOT NULL,
    id_orgao_julgador_atual bigint  NULL,
    id_classe_processual bigint  NULL,
    identificador_externo varchar(300)  NULL,
    grau varchar(20)  NULL,
    data_ajuizamento timestamp  NULL,
    nivel_sigilo integer  NULL,
    codigo_sistema varchar(50)  NULL,
    nome_sistema varchar(100)  NULL,
    formato_processo varchar(50)  NULL,
    data_ultima_atualizacao_fonte timestamp  NULL,
    CONSTRAINT uq_instancia_fonte_identificador UNIQUE (id_fonte, identificador_externo) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT processo_instancia_pk PRIMARY KEY (id_processo_instancia)
);

-- Table: registro_bruto
CREATE TABLE registro_bruto (
    id_registro_bruto bigserial  NOT NULL,
    id_fonte bigint  NOT NULL,
    id_carga bigint  NULL,
    identificador_externo varchar(300)  NULL,
    formato_payload varchar(20)  NOT NULL,
    data_coleta timestamp  NOT NULL,
    hash_conteudo varchar(128)  NULL,
    payload_texto text  NULL,
    payload_jsonb jsonb  NULL,
    CONSTRAINT registro_bruto_pk PRIMARY KEY (id_registro_bruto)
);

-- Table: tribunal
CREATE TABLE tribunal (
    id_tribunal bigserial  NOT NULL,
    sigla varchar(20)  NOT NULL,
    nome varchar(250)  NOT NULL,
    uf char(2)  NULL,
    ramo_justica varchar(80)  NULL,
    esfera varchar(40)  NULL,
    CONSTRAINT uq_tribunal_sigla UNIQUE (sigla) NOT DEFERRABLE  INITIALLY IMMEDIATE,
    CONSTRAINT tribunal_pk PRIMARY KEY (id_tribunal)
);

-- foreign keys
-- Reference: fk_carga_fonte (table: carga_dados)
ALTER TABLE carga_dados ADD CONSTRAINT fk_carga_fonte
    FOREIGN KEY (id_fonte)
    REFERENCES fonte_dados (id_fonte)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_decisao_classe (table: decisao_judicial)
ALTER TABLE decisao_judicial ADD CONSTRAINT fk_decisao_classe
    FOREIGN KEY (id_classe_processual)
    REFERENCES classe_processual (id_classe_processual)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_decisao_fonte (table: decisao_judicial)
ALTER TABLE decisao_judicial ADD CONSTRAINT fk_decisao_fonte
    FOREIGN KEY (id_fonte)
    REFERENCES fonte_dados (id_fonte)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_decisao_orgao (table: decisao_judicial)
ALTER TABLE decisao_judicial ADD CONSTRAINT fk_decisao_orgao
    FOREIGN KEY (id_orgao_julgador)
    REFERENCES orgao_julgador (id_orgao_julgador)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_decisao_pessoa_decisao (table: decisao_pessoa)
ALTER TABLE decisao_pessoa ADD CONSTRAINT fk_decisao_pessoa_decisao
    FOREIGN KEY (id_decisao)
    REFERENCES decisao_judicial (id_decisao)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_decisao_pessoa_pessoa (table: decisao_pessoa)
ALTER TABLE decisao_pessoa ADD CONSTRAINT fk_decisao_pessoa_pessoa
    FOREIGN KEY (id_pessoa)
    REFERENCES pessoa (id_pessoa)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_decisao_processo (table: decisao_judicial)
ALTER TABLE decisao_judicial ADD CONSTRAINT fk_decisao_processo
    FOREIGN KEY (id_processo)
    REFERENCES processo (id_processo)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_decisao_registro_bruto (table: decisao_judicial)
ALTER TABLE decisao_judicial ADD CONSTRAINT fk_decisao_registro_bruto
    FOREIGN KEY (id_registro_bruto)
    REFERENCES registro_bruto (id_registro_bruto)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_decisao_tribunal (table: decisao_judicial)
ALTER TABLE decisao_judicial ADD CONSTRAINT fk_decisao_tribunal
    FOREIGN KEY (id_tribunal)
    REFERENCES tribunal (id_tribunal)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_documento_fonte (table: documento_doutrinario)
ALTER TABLE documento_doutrinario ADD CONSTRAINT fk_documento_fonte
    FOREIGN KEY (id_fonte)
    REFERENCES fonte_dados (id_fonte)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_documento_instituicao (table: documento_doutrinario)
ALTER TABLE documento_doutrinario ADD CONSTRAINT fk_documento_instituicao
    FOREIGN KEY (id_instituicao)
    REFERENCES instituicao (id_instituicao)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_documento_palavra_documento (table: documento_palavra_chave)
ALTER TABLE documento_palavra_chave ADD CONSTRAINT fk_documento_palavra_documento
    FOREIGN KEY (id_documento)
    REFERENCES documento_doutrinario (id_documento)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_documento_palavra_palavra (table: documento_palavra_chave)
ALTER TABLE documento_palavra_chave ADD CONSTRAINT fk_documento_palavra_palavra
    FOREIGN KEY (id_palavra_chave)
    REFERENCES palavra_chave (id_palavra_chave)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_documento_periodico (table: documento_doutrinario)
ALTER TABLE documento_doutrinario ADD CONSTRAINT fk_documento_periodico
    FOREIGN KEY (id_periodico)
    REFERENCES periodico (id_periodico)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_documento_pessoa_documento (table: documento_pessoa)
ALTER TABLE documento_pessoa ADD CONSTRAINT fk_documento_pessoa_documento
    FOREIGN KEY (id_documento)
    REFERENCES documento_doutrinario (id_documento)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_documento_pessoa_pessoa (table: documento_pessoa)
ALTER TABLE documento_pessoa ADD CONSTRAINT fk_documento_pessoa_pessoa
    FOREIGN KEY (id_pessoa)
    REFERENCES pessoa (id_pessoa)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_documento_registro_bruto (table: documento_doutrinario)
ALTER TABLE documento_doutrinario ADD CONSTRAINT fk_documento_registro_bruto
    FOREIGN KEY (id_registro_bruto)
    REFERENCES registro_bruto (id_registro_bruto)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_instancia_classe (table: processo_instancia)
ALTER TABLE processo_instancia ADD CONSTRAINT fk_instancia_classe
    FOREIGN KEY (id_classe_processual)
    REFERENCES classe_processual (id_classe_processual)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_instancia_fonte (table: processo_instancia)
ALTER TABLE processo_instancia ADD CONSTRAINT fk_instancia_fonte
    FOREIGN KEY (id_fonte)
    REFERENCES fonte_dados (id_fonte)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_instancia_orgao_atual (table: processo_instancia)
ALTER TABLE processo_instancia ADD CONSTRAINT fk_instancia_orgao_atual
    FOREIGN KEY (id_orgao_julgador_atual)
    REFERENCES orgao_julgador (id_orgao_julgador)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_instancia_processo (table: processo_instancia)
ALTER TABLE processo_instancia ADD CONSTRAINT fk_instancia_processo
    FOREIGN KEY (id_processo)
    REFERENCES processo (id_processo)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_instancia_registro_bruto (table: processo_instancia)
ALTER TABLE processo_instancia ADD CONSTRAINT fk_instancia_registro_bruto
    FOREIGN KEY (id_registro_bruto)
    REFERENCES registro_bruto (id_registro_bruto)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_instancia_tribunal (table: processo_instancia)
ALTER TABLE processo_instancia ADD CONSTRAINT fk_instancia_tribunal
    FOREIGN KEY (id_tribunal)
    REFERENCES tribunal (id_tribunal)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_movimento_instancia (table: movimento_processual)
ALTER TABLE movimento_processual ADD CONSTRAINT fk_movimento_instancia
    FOREIGN KEY (id_processo_instancia)
    REFERENCES processo_instancia (id_processo_instancia)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_movimento_orgao (table: movimento_processual)
ALTER TABLE movimento_processual ADD CONSTRAINT fk_movimento_orgao
    FOREIGN KEY (id_orgao_julgador)
    REFERENCES orgao_julgador (id_orgao_julgador)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_orgao_tribunal (table: orgao_julgador)
ALTER TABLE orgao_julgador ADD CONSTRAINT fk_orgao_tribunal
    FOREIGN KEY (id_tribunal)
    REFERENCES tribunal (id_tribunal)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_precedente_fonte (table: precedente)
ALTER TABLE precedente ADD CONSTRAINT fk_precedente_fonte
    FOREIGN KEY (id_fonte)
    REFERENCES fonte_dados (id_fonte)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_precedente_processo_precedente (table: precedente_processo)
ALTER TABLE precedente_processo ADD CONSTRAINT fk_precedente_processo_precedente
    FOREIGN KEY (id_precedente)
    REFERENCES precedente (id_precedente)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_precedente_processo_processo (table: precedente_processo)
ALTER TABLE precedente_processo ADD CONSTRAINT fk_precedente_processo_processo
    FOREIGN KEY (id_processo)
    REFERENCES processo (id_processo)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_precedente_registro_bruto (table: precedente)
ALTER TABLE precedente ADD CONSTRAINT fk_precedente_registro_bruto
    FOREIGN KEY (id_registro_bruto)
    REFERENCES registro_bruto (id_registro_bruto)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_precedente_tribunal (table: precedente)
ALTER TABLE precedente ADD CONSTRAINT fk_precedente_tribunal
    FOREIGN KEY (id_tribunal)
    REFERENCES tribunal (id_tribunal)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_processo_assunto_assunto (table: processo_assunto)
ALTER TABLE processo_assunto ADD CONSTRAINT fk_processo_assunto_assunto
    FOREIGN KEY (id_assunto_processual)
    REFERENCES assunto_processual (id_assunto_processual)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_processo_assunto_instancia (table: processo_assunto)
ALTER TABLE processo_assunto ADD CONSTRAINT fk_processo_assunto_instancia
    FOREIGN KEY (id_processo_instancia)
    REFERENCES processo_instancia (id_processo_instancia)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_registro_bruto_carga (table: registro_bruto)
ALTER TABLE registro_bruto ADD CONSTRAINT fk_registro_bruto_carga
    FOREIGN KEY (id_carga)
    REFERENCES carga_dados (id_carga)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- Reference: fk_registro_bruto_fonte (table: registro_bruto)
ALTER TABLE registro_bruto ADD CONSTRAINT fk_registro_bruto_fonte
    FOREIGN KEY (id_fonte)
    REFERENCES fonte_dados (id_fonte)  
    NOT DEFERRABLE 
    INITIALLY IMMEDIATE
;

-- End of file.

