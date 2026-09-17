package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.PrecedenteProcesso;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrecedenteProcessoRepository extends JpaRepository<PrecedenteProcesso,Long> {
    void deleteByIdPrecedente(Long id);
    List<PrecedenteProcesso> findByIdPrecedenteOrderByNumeroRegistro(Long id);
}
