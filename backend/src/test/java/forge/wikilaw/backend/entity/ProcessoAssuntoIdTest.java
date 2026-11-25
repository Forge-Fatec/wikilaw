package forge.wikilaw.backend.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProcessoAssuntoIdTest {

    @Test
    void reconheceChavesComOsMesmosComponentes() {
        Object primeira = new ProcessoAssuntoId(10L, 20L);
        Object segunda = new ProcessoAssuntoId(10L, 20L);

        assertTrue(primeira.equals(segunda));
    }
}
