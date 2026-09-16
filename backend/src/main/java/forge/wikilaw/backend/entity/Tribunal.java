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
@Table(name = "tribunal")
@Getter
@Setter
@NoArgsConstructor
public class Tribunal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tribunal")
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String sigla;

    @Column(nullable = false, length = 250)
    private String nome;

    @Column(length = 2)
    private String uf;

    @Column(name = "ramo_justica", length = 80)
    private String ramoJustica;

    @Column(length = 40)
    private String esfera;
}
