package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.DocumentoDoutrinario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentoDoutrinarioRepository extends JpaRepository<DocumentoDoutrinario, Long>, JpaSpecificationExecutor<DocumentoDoutrinario> {
    Optional<DocumentoDoutrinario> findByIdFonteAndIdentificadorExterno(Long fonte, String identificador);
}
