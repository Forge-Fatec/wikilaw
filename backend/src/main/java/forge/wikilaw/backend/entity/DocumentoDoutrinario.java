package forge.wikilaw.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "documento_doutrinario", uniqueConstraints = @UniqueConstraint(columnNames = {"id_fonte", "identificador_externo"}))
@AttributeOverride(name = "id", column = @Column(name = "id_documento"))
@Getter @Setter
public class DocumentoDoutrinario extends DocumentoBase {
    @Column(name = "tipo_documento", columnDefinition = "text")
    private String tipoDocumento;
    @Column(name = "resumo", columnDefinition = "text")
    private String resumo;
    @Column(name = "autores", columnDefinition = "text")
    private String autores;
    @Column(name = "doi", columnDefinition = "text")
    private String doi;
    @Column(name = "idioma", columnDefinition = "text")
    private String idioma;
    @Column(name = "periodico", columnDefinition = "text")
    private String periodico;
    @Column(name = "issn", columnDefinition = "text")
    private String issn;
    @Column(name = "palavras_chave", columnDefinition = "text")
    private String palavrasChave;
}
