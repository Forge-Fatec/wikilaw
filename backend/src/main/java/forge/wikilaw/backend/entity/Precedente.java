package forge.wikilaw.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "precedente", uniqueConstraints = @UniqueConstraint(columnNames = {"id_fonte", "identificador_externo"}))
@AttributeOverride(name = "id", column = @Column(name = "id_precedente"))
@Getter @Setter
public class Precedente extends DocumentoBase {
    @Column(name = "tribunal_origem", length = 20)
    private String tribunalOrigem;
    @Column(name = "numero_tema", columnDefinition = "text")
    private String numeroTema;
    @Column(name = "tipo_precedente", columnDefinition = "text")
    private String tipoPrecedente;
    @Column(name = "questao_juridica", columnDefinition = "text")
    private String questaoJuridica;
    @Column(name = "tese", columnDefinition = "text")
    private String tese;
    @Column(name = "situacao", columnDefinition = "text")
    private String situacao;
    @Column(name = "data_julgamento", columnDefinition = "date")
    private LocalDate dataJulgamento;
    @Column(name = "id_tribunal", columnDefinition = "bigint")
    private Long idTribunal;
}
