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
@Table(name = "fonte_dados")
@Getter
@Setter
@NoArgsConstructor
public class FonteDados {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fonte")
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, unique = true, length = 30)
    private String sigla;

    @Column(name = "tipo_fonte", nullable = false, length = 40)
    private String tipoFonte;

    @Column(name = "url_base", length = 500)
    private String urlBase;

    @Column(name = "metodo_coleta", length = 30)
    private String metodoColeta;

    @Column(name = "formato_principal", length = 20)
    private String formatoPrincipal;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(columnDefinition = "text")
    private String observacao;
}
