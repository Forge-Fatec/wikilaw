package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.DecisaoJudicial;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DecisaoJudicialRepository extends JpaRepository<DecisaoJudicial, Long>, JpaSpecificationExecutor<DecisaoJudicial> {
    Optional<DecisaoJudicial> findByIdFonteAndIdentificadorExterno(Long fonte, String identificador);
    long countByIdFonteAndAtivoTrue(Long fonte);
}
