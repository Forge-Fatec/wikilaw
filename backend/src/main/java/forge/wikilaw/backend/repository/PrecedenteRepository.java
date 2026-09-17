package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.Precedente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PrecedenteRepository extends JpaRepository<Precedente, Long>, JpaSpecificationExecutor<Precedente> {
    Optional<Precedente> findByIdFonteAndIdentificadorExterno(Long fonte, String identificador);
}
