package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.model.DocumentoDoutrinario;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoDoutrinarioRepository extends JpaRepository<DocumentoDoutrinario, Long> {

    /**
     * SCRUM-129: busca de doutrinas por termo livre.
     * Procura o termo tanto no título quanto no resumo, ignorando maiúsculas/minúsculas.
     *
     * Ex: findByTituloContainingIgnoreCaseOrResumoContainingIgnoreCase("dano moral", "dano moral")
     * gera algo como:
     * WHERE lower(titulo) LIKE lower('%dano moral%') OR lower(resumo) LIKE lower('%dano moral%')
     */
    List<DocumentoDoutrinario> findByTituloContainingIgnoreCaseOrResumoContainingIgnoreCase(
            String termoTitulo, String termoResumo);
}