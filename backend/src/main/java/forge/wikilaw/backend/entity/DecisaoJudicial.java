package forge.wikilaw.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "decisao_judicial", uniqueConstraints = @UniqueConstraint(columnNames = {"id_fonte", "identificador_externo"}))
@AttributeOverride(name = "id", column = @Column(name = "id_decisao"))
@Getter @Setter
public class DecisaoJudicial extends DocumentoBase {
    @Column(name = "numero_processo", columnDefinition = "varchar(100)")
    private String numeroProcesso;
    @Column(name = "tipo_decisao", columnDefinition = "text")
    private String tipoDecisao;
    @Column(name = "ementa", columnDefinition = "text")
    private String ementa;
    @Column(name = "relator", columnDefinition = "text")
    private String relator;
    @Column(name = "orgao_julgador", columnDefinition = "text")
    private String orgaoJulgador;
    @Column(name = "data_julgamento", columnDefinition = "date")
    private LocalDate dataJulgamento;
    @Column(name = "decisao", columnDefinition = "text")
    private String decisao;
    @Column(name = "inteiro_teor", columnDefinition = "text")
    private String inteiroTeor;
    @Column(name = "possui_inteiro_teor", columnDefinition = "boolean")
    private boolean possuiInteiroTeor;
    @Column(name = "id_processo", columnDefinition = "bigint")
    private Long idProcesso;
    @Column(name = "id_tribunal", columnDefinition = "bigint")
    private Long idTribunal;
}
