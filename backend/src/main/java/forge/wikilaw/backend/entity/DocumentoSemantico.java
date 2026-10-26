package forge.wikilaw.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "documento_semantico", uniqueConstraints = {
    @UniqueConstraint(columnNames = "id_decisao"),
    @UniqueConstraint(columnNames = "id_precedente"),
    @UniqueConstraint(columnNames = "id_documento_doutrinario")
})
@Getter
@Setter
@NoArgsConstructor
public class DocumentoSemantico {
    public enum Categoria { JURISPRUDENCIA, PRECEDENTE, DOUTRINA }
    public enum Status { PENDENTE, PROCESSANDO, PRONTO, ERRO, INATIVO }
    public enum StatusEmbedding { PENDENTE, PROCESSANDO, PRONTO, ERRO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_documento_semantico")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Categoria categoria;

    @Column(name = "id_decisao")
    private Long idDecisao;

    @Column(name = "id_precedente")
    private Long idPrecedente;

    @Column(name = "id_documento_doutrinario")
    private Long idDocumentoDoutrinario;

    @Column(name = "hash_conteudo", length = 64)
    private String hashConteudo;

    @Column(name = "versao_preparacao", nullable = false, length = 20)
    private String versaoPreparacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDENTE;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metadados = "{}";

    @Column(name = "mensagem_erro", columnDefinition = "text")
    private String mensagemErro;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_embedding", nullable = false, length = 20)
    private StatusEmbedding statusEmbedding = StatusEmbedding.PENDENTE;

    @Column(name = "modelo_embedding", length = 120)
    private String modeloEmbedding;

    @Column(name = "tentativas_embedding", nullable = false)
    private int tentativasEmbedding;

    @Column(name = "erro_embedding", columnDefinition = "text")
    private String erroEmbedding;

    @Column(name = "embedding_atualizado_em")
    private OffsetDateTime embeddingAtualizadoEm;

    /** Os trechos foram recriados, entao os vetores anteriores deixam de valer. */
    public void marcarEmbeddingPendente() {
        statusEmbedding = StatusEmbedding.PENDENTE;
        modeloEmbedding = null;
        tentativasEmbedding = 0;
        erroEmbedding = null;
        embeddingAtualizadoEm = null;
    }
}

