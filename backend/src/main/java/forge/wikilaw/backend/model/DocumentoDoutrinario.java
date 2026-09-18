package forge.wikilaw.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mapeia a tabela documento_doutrinario (database_init.sql).
 *
 * Assim como em DecisaoJudicial, os campos de relacionamento (id_fonte,
 * id_registro_bruto, id_instituicao, id_periodico) ainda estão como Long
 * simples, porque as entidades correspondentes ainda não existem.
 */
@Entity
@Table(name = "documento_doutrinario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoDoutrinario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento")
    private Long idDocumento;

    @Column(name = "id_fonte", nullable = false)
    private Long idFonte;

    @Column(name = "id_registro_bruto")
    private Long idRegistroBruto;

    @Column(name = "id_instituicao")
    private Long idInstituicao;

    @Column(name = "id_periodico")
    private Long idPeriodico;

    @Column(name = "identificador_externo", length = 300)
    private String identificadorExterno;

    @Column(name = "oai_identifier", length = 300)
    private String oaiIdentifier;

    @Column(name = "tipo_documento", length = 80, nullable = false)
    private String tipoDocumento;

    @Column(name = "titulo", length = 1000, nullable = false)
    private String titulo;

    @Column(name = "subtitulo", length = 1000)
    private String subtitulo;

    @Column(name = "resumo", columnDefinition = "text")
    private String resumo;

    @Column(name = "idioma", length = 20)
    private String idioma;

    @Column(name = "ano_publicacao")
    private Integer anoPublicacao;

    @Column(name = "data_publicacao")
    private LocalDate dataPublicacao;

    @Column(name = "ano_defesa")
    private Integer anoDefesa;

    @Column(name = "doi", length = 200)
    private String doi;

    @Column(name = "isbn", length = 50)
    private String isbn;

    @Column(name = "volume", length = 50)
    private String volume;

    @Column(name = "numero_edicao", length = 50)
    private String numeroEdicao;

    @Column(name = "elocation", length = 100)
    private String elocation;

    @Column(name = "paginas", length = 100)
    private String paginas;

    @Column(name = "programa_pos_graduacao", length = 500)
    private String programaPosGraduacao;

    @Column(name = "departamento", length = 500)
    private String departamento;

    @Column(name = "colecao", length = 500)
    private String colecao;

    @Column(name = "fonte_bibliografica", columnDefinition = "text")
    private String fonteBibliografica;

    @Column(name = "notas", columnDefinition = "text")
    private String notas;

    @Column(name = "tipo_acesso", length = 80)
    private String tipoAcesso;

    @Column(name = "acesso_aberto")
    private Boolean acessoAberto;

    @Column(name = "licenca", length = 200)
    private String licenca;

    @Column(name = "url_original", length = 1000)
    private String urlOriginal;

    @Column(name = "url_texto_completo", length = 1000)
    private String urlTextoCompleto;
}