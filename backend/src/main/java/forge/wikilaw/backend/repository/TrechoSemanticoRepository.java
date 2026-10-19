package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.TrechoSemantico;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrechoSemanticoRepository extends JpaRepository<TrechoSemantico, Long> {
    List<TrechoSemantico> findByIdDocumentoSemanticoOrderByOrdem(Long idDocumentoSemantico);
    void deleteByIdDocumentoSemantico(Long idDocumentoSemantico);
    long countByIdDocumentoSemantico(Long idDocumentoSemantico);
}
