package forge.wikilaw.backend.entity;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Campos internos comuns; nenhum campo de formato externo vaza para as entidades. */
@MappedSuperclass
@Getter @Setter
public abstract class DocumentoBase {
    @Column(nullable = false)
    private boolean ativo = true;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "id_fonte", nullable = false)
    private Long idFonte;
    @Column(name = "id_registro_bruto", nullable = false)
    private Long idRegistroBruto;
    @Column(name = "identificador_externo", nullable = false, length = 300)
    private String identificadorExterno;
    @Column(nullable = false, columnDefinition = "text")
    private String titulo;
    @Column(name = "url_original", columnDefinition = "text")
    private String urlOriginal;
    @Column(name = "data_publicacao")
    private LocalDate dataPublicacao;
    @Column(name = "data_original", columnDefinition = "text")
    private String dataOriginal;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadados;
}
