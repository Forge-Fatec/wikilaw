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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "registro_bruto")
@Getter
@Setter
@NoArgsConstructor
public class RegistroBruto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro_bruto")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_fonte", nullable = false)
    private FonteDados fonte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_carga")
    private CargaDados carga;

    @Column(name = "identificador_externo", length = 300)
    private String identificadorExterno;

    @Column(name = "formato_payload", nullable = false, length = 20)
    private String formatoPayload;

    @Column(name = "data_coleta", nullable = false)
    private OffsetDateTime dataColeta;

    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String hashConteudo;

    @Column(name = "payload_texto", columnDefinition = "text")
    private String payloadTexto;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_jsonb", columnDefinition = "jsonb")
    private String payloadJsonb;
}
