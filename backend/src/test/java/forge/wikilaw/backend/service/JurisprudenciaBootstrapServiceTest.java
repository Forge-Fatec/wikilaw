package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import forge.wikilaw.backend.config.JurisprudenciaBootstrapProperties;
import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JurisprudenciaBootstrapServiceTest {
    private final ImportJobService jobs = mock(ImportJobService.class);
    private final FonteDadosRepository fontes = mock(FonteDadosRepository.class);
    private final DecisaoJudicialRepository decisoes = mock(DecisaoJudicialRepository.class);
    private final PrecedenteRepository precedentes = mock(PrecedenteRepository.class);
    private final DocumentoDoutrinarioRepository doutrina = mock(DocumentoDoutrinarioRepository.class);
    private final JurisprudenciaBootstrapProperties properties = new JurisprudenciaBootstrapProperties();
    private JurisprudenciaBootstrapService bootstrap;

    @BeforeEach
    void setup() {
        properties.setEnabled(false);
        properties.setCompleto(false);
        properties.setPaginas(3);
        properties.setTamanhoPagina(20);
        properties.setSomenteSeVazio(true);
        for (var source : DocumentSource.values()) {
            var f = new FonteDados(); f.setId((long) source.ordinal() + 1);
            when(fontes.findBySigla(source.sigla())).thenReturn(Optional.of(f));
        }
        bootstrap = new JurisprudenciaBootstrapService(properties, jobs, fontes, decisoes, precedentes, doutrina);
    }

    @Test
    void queuesEverySourceWithoutWaitingForImports() {
        properties.setEnabled(true);
        bootstrap.aoIniciar();
        var sources = ArgumentCaptor.forClass(DocumentSource.class);
        verify(jobs, times(7)).enqueueDocuments(sources.capture(), any(), eq(3));
        assertThat(sources.getAllValues()).containsExactly(DocumentSource.TJDFT, DocumentSource.STJ,
                DocumentSource.STJ_PRECEDENTES, DocumentSource.PANGEA, DocumentSource.BDJUR,
                DocumentSource.SCIELO, DocumentSource.BDTD);
    }

    @Test
    void skipsExistingCategoryAndDisabledBootstrap() {
        bootstrap.aoIniciar();
        verifyNoInteractions(jobs);
        when(precedentes.countByIdFonteAndAtivoTrue((long) DocumentSource.PANGEA.ordinal() + 1)).thenReturn(5L);
        bootstrap.importarFonte(DocumentSource.PANGEA);
        verifyNoInteractions(jobs);
    }

    @Test
    void continuesWhenOneSourceCannotBeQueued() {
        when(jobs.enqueueDocuments(eq(DocumentSource.TJDFT), any(), anyInt()))
                .thenThrow(new IllegalStateException("Tarefa já existente"));
        bootstrap.importarPaginas();
        verify(jobs).enqueueDocuments(eq(DocumentSource.STJ), any(), eq(3));
        verify(jobs).enqueueDocuments(eq(DocumentSource.BDTD), any(), eq(3));
    }

    @Test
    void fullBootstrapRemovesTermAndDoesNotSkipPartiallyPopulatedCategory() {
        properties.setCompleto(true);
        properties.setEnabled(true);
        when(precedentes.countByIdFonteAndAtivoTrue((long) DocumentSource.PANGEA.ordinal() + 1)).thenReturn(5L);
        bootstrap.aoIniciar();
        var requests = ArgumentCaptor.forClass(DocumentImportRequest.class);
        verify(jobs,times(7)).ensureDocumentsComplete(any(),requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.fullCollection()).isTrue();
            assertThat(request.termo()).isNull();
            assertThat(request.query()).isEmpty();
        });
        verify(jobs,never()).enqueueDocuments(any(),any(),anyInt());
    }
}
