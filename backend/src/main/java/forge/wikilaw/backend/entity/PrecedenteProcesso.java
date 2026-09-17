package forge.wikilaw.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="precedente_processo",
    uniqueConstraints=@UniqueConstraint(columnNames={"id_precedente","numero_registro"}))
@Getter @Setter
public class PrecedenteProcesso {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id_vinculo")
    private Long id;
    @Column(name="id_precedente",nullable=false)
    private Long idPrecedente;
    @Column(name="id_registro_bruto",nullable=false)
    private Long idRegistroBruto;
    @Column(name="numero_registro",nullable=false,length=100)
    private String numeroRegistro;
    @Column(columnDefinition="text")
    private String descricao;
    @Column(columnDefinition="text")
    private String relator;
    @Column(name="leading_case",length=20)
    private String leadingCase;
}
