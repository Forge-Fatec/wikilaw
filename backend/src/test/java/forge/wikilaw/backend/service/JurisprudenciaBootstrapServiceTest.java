package forge.wikilaw.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.config.JurisprudenciaBootstrapProperties;
import forge.wikilaw.backend.entity.CargaStatus;
import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.integration.documents.DocumentImportRequest;
import forge.wikilaw.backend.integration.documents.DocumentSource;
import forge.wikilaw.backend.integration.documents.SourcePage;
import forge.wikilaw.backend.repository.DecisaoJudicialRepository;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JurisprudenciaBootstrapServiceTest {

    private final DocumentImportService importService = mock(DocumentImportService.class);
    private final FonteDadosRepository fontes = mock(FonteDadosRepository.class);
    private final DecisaoJudicialRepository decisoes = mock(DecisaoJudicialRepository.class);
    private final JurisprudenciaBootstrapProperties properties = new JurisprudenciaBootstrapProperties();
    private JurisprudenciaBootstrapService bootstrap;
    private FonteDados fonte;

    @BeforeEach
    void configurar() {
        properties.setTermo("dano moral");
        properties.setPaginas(3);
        properties.setTamanhoPagina(20);
        properties.setSomenteSeVazio(true);

        fonte = new FonteDados();
        fonte.setId(3L);
        fonte.setSigla("TJDFT");
        fonte.setNome("Jurisprudência TJDFT");
        fonte.setTipoFonte("JURISPRUDENCIA");
        when(fontes.findBySigla("TJDFT")).thenReturn(Optional.of(fonte));

        bootstrap = new JurisprudenciaBootstrapService(properties, importService, fontes, decisoes);
    }

    @Test
    void importaQuantidadeConfiguradaDePaginas() {
        when(decisoes.countByIdFonteAndAtivoTrue(3L)).thenReturn(0L);
        when(importService.importar(any(), any()))
                .thenReturn(resultado(1L, null))
                .thenReturn(resultado(2L, null))
                .thenReturn(resultado(3L, null));

        bootstrap.importarPaginas();

        var captor = ArgumentCaptor.forClass(DocumentImportRequest.class);
        verify(importService, org.mockito.Mockito.times(3))
                .importar(org.mockito.ArgumentMatchers.eq(DocumentSource.TJDFT), captor.capture());
        assertEquals(0, captor.getAllValues().get(0).pagina());
        assertEquals(1, captor.getAllValues().get(1).pagina());
        assertEquals(2, captor.getAllValues().get(2).pagina());
        assertEquals("dano moral", captor.getAllValues().get(0).termo());
        assertEquals(20, captor.getAllValues().get(0).tamanhoPagina());
    }

    @Test
    void interrompeQuandoUmaPaginaFalha() {
        when(decisoes.countByIdFonteAndAtivoTrue(3L)).thenReturn(0L);
        when(importService.importar(any(), any()))
                .thenReturn(resultado(1L, null))
                .thenReturn(new DocumentImportService.ImportResult(
                        2L,
                        DocumentSource.TJDFT,
                        CargaStatus.FALHA,
                        0,
                        0,
                        1,
                        "Falha externa",
                        List.of(),
                        null,
                        List.of()));

        bootstrap.importarPaginas();

        verify(importService, org.mockito.Mockito.times(2)).importar(any(), any());
    }

    @Test
    void naoImportaQuandoJaExistemDecisoesAtivas() {
        when(decisoes.countByIdFonteAndAtivoTrue(3L)).thenReturn(5L);

        bootstrap.importarPaginas();

        verify(importService, never()).importar(any(), any());
    }

    private DocumentImportService.ImportResult resultado(Long id, SourcePage.Continuacao proxima) {
        return new DocumentImportService.ImportResult(
                id,
                DocumentSource.TJDFT,
                CargaStatus.CONCLUIDA,
                20,
                20,
                0,
                null,
                List.of(id),
                proxima,
                List.of());
    }
}
