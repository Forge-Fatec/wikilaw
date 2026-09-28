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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orgao_julgador", uniqueConstraints =
        @UniqueConstraint(name = "uq_orgao_tribunal_codigo", columnNames = {"id_tribunal", "codigo_externo"}))
@Getter
@Setter
@NoArgsConstructor
public class OrgaoJulgador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orgao_julgador")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tribunal", nullable = false)
    private Tribunal tribunal;

    @Column(name = "codigo_externo", nullable = false, length = 50)
    private String codigoExterno;

    @Column(nullable = false, length = 250)
    private String nome;

    @Column(name = "codigo_municipio_ibge")
    private Long codigoMunicipioIbge;
}
