package forge.wikilaw.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ProcessoAssuntoId implements Serializable {

    @Column(name = "id_processo_instancia")
    private Long processoInstanciaId;

    @Column(name = "id_assunto_processual")
    private Long assuntoProcessualId;

    public ProcessoAssuntoId(Long processoInstanciaId, Long assuntoProcessualId) {
        this.processoInstanciaId = processoInstanciaId;
        this.assuntoProcessualId = assuntoProcessualId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProcessoAssuntoId that)) {
            return false;
        }
        return Objects.equals(processoInstanciaId, that.processoInstanciaId)
                && Objects.equals(assuntoProcessualId, that.assuntoProcessualId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(processoInstanciaId, assuntoProcessualId);
    }
}
