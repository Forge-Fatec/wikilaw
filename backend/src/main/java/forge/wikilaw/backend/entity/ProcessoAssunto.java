package forge.wikilaw.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "processo_assunto")
@Getter
@Setter
@NoArgsConstructor
public class ProcessoAssunto {

    @EmbeddedId
    private ProcessoAssuntoId id;

    @MapsId("processoInstanciaId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_processo_instancia", nullable = false)
    private ProcessoInstancia processoInstancia;

    @MapsId("assuntoProcessualId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_assunto_processual", nullable = false)
    private AssuntoProcessual assuntoProcessual;

    @Column(nullable = false)
    private boolean principal;
}
