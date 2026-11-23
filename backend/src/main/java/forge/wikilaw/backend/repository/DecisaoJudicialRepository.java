package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.DecisaoJudicial;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DecisaoJudicialRepository extends JpaRepository<DecisaoJudicial, Long>, JpaSpecificationExecutor<DecisaoJudicial> {
    Optional<DecisaoJudicial> findByIdFonteAndIdentificadorExterno(Long fonte, String identificador);
    long countByIdFonteAndAtivoTrue(Long fonte);

    /** Maior id ja gravado (0 sem documentos): todo documento importado depois tem id maior. */
    @Query("select coalesce(max(d.id), 0) from DecisaoJudicial d")
    long findMaxId();
}
