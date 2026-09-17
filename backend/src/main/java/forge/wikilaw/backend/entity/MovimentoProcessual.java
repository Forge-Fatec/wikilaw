package forge.wikilaw.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "movimento_processual")
@Getter
@Setter
@NoArgsConstructor
public class MovimentoProcessual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_processo_instancia", nullable = false)
    private ProcessoInstancia processoInstancia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orgao_julgador")
    private OrgaoJulgador orgaoJulgador;

    @Column(name = "codigo_cnj")
    private Long codigoCnj;

    @Column(length = 500)
    private String nome;

    @Column(name = "data_hora")
    private OffsetDateTime dataHora;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "complementos_jsonb", columnDefinition = "jsonb")
    private String complementosJsonb;
}
