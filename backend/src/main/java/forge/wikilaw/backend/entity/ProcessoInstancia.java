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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "processo_instancia", uniqueConstraints =
        @UniqueConstraint(name = "uq_instancia_fonte_identificador",
                columnNames = {"id_fonte", "identificador_externo"}))
@Getter
@Setter
@NoArgsConstructor
public class ProcessoInstancia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_processo_instancia")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_processo", nullable = false)
    private Processo processo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fonte", nullable = false)
    private FonteDados fonte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_registro_bruto")
    private RegistroBruto registroBruto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tribunal", nullable = false)
    private Tribunal tribunal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_orgao_julgador_atual")
    private OrgaoJulgador orgaoJulgadorAtual;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_classe_processual")
    private ClasseProcessual classeProcessual;

    @Column(name = "identificador_externo", nullable = false, length = 300)
    private String identificadorExterno;

    @Column(length = 20)
    private String grau;

    @Column(name = "data_ajuizamento")
    private OffsetDateTime dataAjuizamento;

    @Column(name = "nivel_sigilo")
    private Integer nivelSigilo;

    @Column(name = "codigo_sistema", length = 50)
    private String codigoSistema;

    @Column(name = "nome_sistema", length = 100)
    private String nomeSistema;

    @Column(name = "formato_processo", length = 50)
    private String formatoProcesso;

    @Column(name = "data_ultima_atualizacao_fonte")
    private OffsetDateTime dataUltimaAtualizacaoFonte;
}
