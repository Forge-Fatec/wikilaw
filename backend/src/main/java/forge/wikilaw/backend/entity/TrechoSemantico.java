package forge.wikilaw.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trecho_semantico", uniqueConstraints =
        @UniqueConstraint(columnNames = {"id_documento_semantico", "ordem"}))
@Getter
@Setter
@NoArgsConstructor
public class TrechoSemantico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_trecho_semantico")
    private Long id;

    @Column(name = "id_documento_semantico", nullable = false)
    private Long idDocumentoSemantico;

    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false, length = 40)
    private String secao;

    @Column(nullable = false, columnDefinition = "text")
    private String texto;

    @Column(name = "texto_para_embedding", nullable = false, columnDefinition = "text")
    private String textoParaEmbedding;

    @Column(name = "quantidade_tokens", nullable = false)
    private int quantidadeTokens;

    @Column(name = "hash_texto", nullable = false, length = 64)
    private String hashTexto;
}

