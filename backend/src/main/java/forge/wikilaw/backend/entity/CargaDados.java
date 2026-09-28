package forge.wikilaw.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "carga_dados")
@Getter
@Setter
@NoArgsConstructor
public class CargaDados {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_carga")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fonte", nullable = false)
    private FonteDados fonte;

    @Column(name = "data_inicio", nullable = false)
    private OffsetDateTime dataInicio;

    @Column(name = "data_fim")
    private OffsetDateTime dataFim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CargaStatus status;

    @Column(name = "quantidade_recebida", nullable = false)
    private int quantidadeRecebida;

    @Column(name = "quantidade_processada", nullable = false)
    private int quantidadeProcessada;

    @Column(name = "quantidade_erro", nullable = false)
    private int quantidadeErro;

    @Column(name = "mensagem_erro", columnDefinition = "text")
    private String mensagemErro;
}
