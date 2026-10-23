package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.DocumentoSemantico;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoSemanticoRepository extends JpaRepository<DocumentoSemantico, Long> {
    Optional<DocumentoSemantico> findByIdDecisao(Long id);
    Optional<DocumentoSemantico> findByIdPrecedente(Long id);
    Optional<DocumentoSemantico> findByIdDocumentoDoutrinario(Long id);

    @Query(value = """
            select d.id_decisao from decisao_judicial d
            where d.ativo and not exists (
                select 1 from documento_semantico s
                where s.id_decisao = d.id_decisao
                  and s.versao_preparacao = :versao
                  and s.status in ('PRONTO', 'ERRO')
            )
            order by d.id_decisao limit :limite for update skip locked
            """, nativeQuery = true)
    List<Long> findDecisoesPendentes(@Param("versao") String versao, @Param("limite") int limite);

    @Query(value = """
            select p.id_precedente from precedente p
            where p.ativo and not exists (
                select 1 from documento_semantico s
                where s.id_precedente = p.id_precedente
                  and s.versao_preparacao = :versao
                  and s.status in ('PRONTO', 'ERRO')
            )
            order by p.id_precedente limit :limite for update skip locked
            """, nativeQuery = true)
    List<Long> findPrecedentesPendentes(@Param("versao") String versao, @Param("limite") int limite);

    @Query(value = """
            select d.id_documento from documento_doutrinario d
            where d.ativo and not exists (
                select 1 from documento_semantico s
                where s.id_documento_doutrinario = d.id_documento
                  and s.versao_preparacao = :versao
                  and s.status in ('PRONTO', 'ERRO')
            )
            order by d.id_documento limit :limite for update skip locked
            """, nativeQuery = true)
    List<Long> findDoutrinasPendentes(@Param("versao") String versao, @Param("limite") int limite);

    /**
     * Documentos preparados que precisam de vetores: pendentes, gerados com outro modelo,
     * com erro elegivel a nova tentativa ou abandonados em PROCESSANDO.
     */
    @Query(value = """
            select s.id_documento_semantico from documento_semantico s
            where s.status = 'PRONTO' and (
                s.status_embedding = 'PENDENTE'
                or (s.status_embedding = 'PRONTO'
                    and s.modelo_embedding is distinct from :modelo)
                or (s.status_embedding = 'ERRO'
                    and (s.tentativas_embedding < :maxTentativas
                         or s.modelo_embedding is distinct from :modelo)
                    and (s.embedding_atualizado_em is null
                         or s.embedding_atualizado_em < :novaTentativaAntesDe))
                or (s.status_embedding = 'PROCESSANDO'
                    and (s.embedding_atualizado_em is null
                         or s.embedding_atualizado_em < :abandonadoAntesDe))
            )
            order by s.id_documento_semantico limit :limite for update skip locked
            """, nativeQuery = true)
    List<Long> findEmbeddingPendentes(@Param("modelo") String modelo,
            @Param("maxTentativas") int maxTentativas,
            @Param("novaTentativaAntesDe") OffsetDateTime novaTentativaAntesDe,
            @Param("abandonadoAntesDe") OffsetDateTime abandonadoAntesDe,
            @Param("limite") int limite);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentoSemantico s where s.id = :id")
    Optional<DocumentoSemantico> findByIdForUpdate(@Param("id") Long id);
}

