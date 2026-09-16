package forge.wikilaw.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "classe_processual")
@Getter
@Setter
@NoArgsConstructor
public class ClasseProcessual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_classe_processual")
    private Long id;

    @Column(name = "codigo_cnj", nullable = false, unique = true)
    private Long codigoCnj;

    @Column(nullable = false, length = 250)
    private String nome;
}
