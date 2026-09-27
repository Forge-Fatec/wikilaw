package forge.wikilaw.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import forge.wikilaw.backend.repository.DocumentoDoutrinarioRepository;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import forge.wikilaw.backend.repository.PrecedenteRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JurisprudenciaBootstrapServiceTest {

    private final DocumentImportService importService = mock(DocumentImportService.class);
    private final FonteDadosRepository fontes = mock(FonteDadosRepository.class);
    private final DecisaoJudicialRepository decisoes = mock(DecisaoJudicialRepository.class);
    private final PrecedenteRepository precedentes = mock(PrecedenteRepository.class);
    private final DocumentoDoutrinarioRepository doutrina = mock(DocumentoDoutrinarioRepository.class);
    private final JurisprudenciaBootstrapProperties properties = new JurisprudenciaBootstrapProperties();
    private JurisprudenciaBootstrapService bootstrap;

    @BeforeEach
    void configurar() {
        properties.setTermo("dano moral");
        properties.setPaginas(1);
        properties.setTamanhoPagina(20);
        properties.setSomenteSeVazio(true);

        for (DocumentSource source : DocumentSource.values()) {
            when(fontes.findBySigla(source.sigla())).thenReturn(Optional.of(fonte(source)));
        }
        when(importService.importar(any(), any())).thenAnswer(invocation ->
                resultado(invocation.getArgument(0), 1L, null));

        bootstrap = new JurisprudenciaBootstrapService(
                properties, importService, fontes, decisoes, precedentes, doutrina);
    }

    @Test
    void importaTodasAsFontesDocumentaisSemDataJud() {
        bootstrap.importarPaginas();

        var captor = ArgumentCaptor.forClass(DocumentSource.class);
        verify(importService, org.mockito.Mockito.times(7)).importar(captor.capture(), any());
        assertIterableEquals(
                List.of(
                        DocumentSource.TJDFT,
                        DocumentSource.STJ,
                        DocumentSource.STJ_PRECEDENTES,
                        DocumentSource.PANGEA,
                        DocumentSource.BDJUR,
                        DocumentSource.SCIELO,
                        DocumentSource.BDTD),
                captor.getAllValues());
    }

    @Test
    void usaContinuacaoDaFonteParaProximaEtapa() {
        properties.setPaginas(3);
        var continuacao = new SourcePage.Continuacao(null, 20, null, "123e4567-e89b-12d3-a456-426614174000");
        when(importService.importar(eq(DocumentSource.STJ), any()))
                .thenReturn(resultado(DocumentSource.STJ, 1L, continuacao))
                .thenReturn(resultado(DocumentSource.STJ, 2L, null));

        bootstrap.importarFonte(DocumentSource.STJ);

        var captor = ArgumentCaptor.forClass(DocumentImportRequest.class);
        verify(importService, org.mockito.Mockito.times(2)).importar(eq(DocumentSource.STJ), captor.capture());
        assertEquals(0, captor.getAllValues().get(0).offset());
        assertEquals(20, captor.getAllValues().get(1).offset());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", captor.getAllValues().get(1).recursoId());
    }

    @Test
    void naoImportaFonteQuandoJaExistemDocumentosAtivosNaCategoria() {
        when(precedentes.countByIdFonteAndAtivoTrue(fonte(DocumentSource.PANGEA).getId())).thenReturn(5L);

        bootstrap.importarFonte(DocumentSource.PANGEA);

        verify(importService, never()).importar(eq(DocumentSource.PANGEA), any());
    }

    @Test
    void continuaProximasFontesQuandoUmaFonteFalha() {
        when(importService.importar(eq(DocumentSource.TJDFT), any()))
                .thenThrow(new IllegalStateException("fora do ar"));

        bootstrap.importarPaginas();

        verify(importService).importar(eq(DocumentSource.STJ), any());
        verify(importService).importar(eq(DocumentSource.BDTD), any());
    }

    private FonteDados fonte(DocumentSource source) {
        var fonte = new FonteDados();
        fonte.setId((long) source.ordinal() + 1);
        fonte.setSigla(source.sigla());
        fonte.setNome(source.name());
        fonte.setTipoFonte("TESTE");
        return fonte;
    }

    private DocumentImportService.ImportResult resultado(
            DocumentSource source,
            Long id,
            SourcePage.Continuacao proxima) {
        return new DocumentImportService.ImportResult(
                id,
                source,
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
