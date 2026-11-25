package forge.wikilaw.backend.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcessoAssuntoIdMutationTest {

    @Test
    void igualdadeViaObjectConsideraCadaComponenteDaChave() {
        Object chave = new ProcessoAssuntoId(10L, 20L);

        assertFalse(chave.equals(new ProcessoAssuntoId(11L, 20L)));
        assertFalse(chave.equals(new ProcessoAssuntoId(10L, 21L)));
        assertFalse(chave.equals(null));
        assertFalse(chave.equals("10:20"));
        assertTrue(chave.equals(chave));
    }

    @Test
    void hashSetEliminaDuplicatasEMantemChavesDistintas() {
        Set<ProcessoAssuntoId> chaves = new HashSet<>();

        assertTrue(chaves.add(new ProcessoAssuntoId(10L, 20L)));
        assertFalse(chaves.add(new ProcessoAssuntoId(10L, 20L)));
        assertTrue(chaves.add(new ProcessoAssuntoId(11L, 20L)));
        assertTrue(chaves.add(new ProcessoAssuntoId(10L, 21L)));
        assertEquals(3, chaves.size());
        assertTrue(chaves.contains(new ProcessoAssuntoId(10L, 20L)));
    }

    @Test
    void chavesIguaisTemMesmoHashESuportamComponentesNulos() {
        var primeira = new ProcessoAssuntoId(10L, null);
        var segunda = new ProcessoAssuntoId(10L, null);

        assertEquals(primeira, segunda);
        assertEquals(segunda, primeira);
        assertEquals(primeira.hashCode(), segunda.hashCode());
        assertEquals(new ProcessoAssuntoId(null, null), new ProcessoAssuntoId(null, null));
        assertNotEquals(primeira, new ProcessoAssuntoId(null, 20L));
        assertNotEquals(primeira, new ProcessoAssuntoId(10L, 20L));
    }
}
