package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.OrgaoJulgador;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgaoJulgadorRepository extends JpaRepository<OrgaoJulgador, Long> {
    Optional<OrgaoJulgador> findByTribunalIdAndCodigoExterno(Long tribunalId, String codigoExterno);
}
