package forge.wikilaw.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Durable page checkpoint; business records commit independently. */
@Entity
@Table(name = "import_job")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob {
    public enum Status { PENDENTE, EM_EXECUCAO, CONCLUIDA, CONCLUIDA_COM_ERROS, FALHA, LIMITE_ATINGIDO, CANCELADA }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String fonte;
    @Column(name = "escopo", nullable = false, length = 100)
    private String escopo;
    @Column(name = "incremental_base", nullable = false)
    private boolean incrementalBase;
    @Column(name = "ate_esgotar", nullable = false)
    private boolean ateEsgotar;
    @Column(name = "bootstrap_completo", nullable = false)
    private boolean bootstrapCompleto;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Status status = Status.PENDENTE;
    @Column(name = "requisicao", nullable = false, columnDefinition = "text")
    private String requisicao;
    @Column(name = "cursor_json", columnDefinition = "text")
    private String cursorJson;
    @Column(name = "cache_json", nullable = false, columnDefinition = "text")
    private String cacheJson = "{}";
    @Column(name = "maximo_paginas", nullable = false)
    private int maximoPaginas;
    @Column(name = "paginas", nullable = false)
    private long paginas;
    @Column(nullable = false)
    private long recebidos;
    @Column(nullable = false)
    private long processados;
    @Column(nullable = false)
    private long erros;
    @Column(name = "ultima_carga")
    private Long ultimaCarga;
    @Column(name = "mensagem", columnDefinition = "text")
    private String mensagem;
    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;
}
