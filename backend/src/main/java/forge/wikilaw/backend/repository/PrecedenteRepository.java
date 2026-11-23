package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.Precedente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface PrecedenteRepository extends JpaRepository<Precedente, Long>, JpaSpecificationExecutor<Precedente> {
    Optional<Precedente> findByIdFonteAndIdentificadorExterno(Long fonte, String identificador);
    long countByIdFonteAndAtivoTrue(Long fonte);

    /** Maior id ja gravado (0 sem documentos): todo documento importado depois tem id maior. */
    @Query("select coalesce(max(d.id), 0) from Precedente d")
    long findMaxId();
}
