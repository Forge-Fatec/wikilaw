package forge.wikilaw.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.entity.Processo;
import forge.wikilaw.backend.entity.ProcessoInstancia;
import forge.wikilaw.backend.entity.RegistroBruto;
import forge.wikilaw.backend.entity.Tribunal;
import forge.wikilaw.backend.integration.NormalizedProcessRecord;
import forge.wikilaw.backend.integration.datajud.DataJudTribunal;
import forge.wikilaw.backend.repository.AssuntoProcessualRepository;
import forge.wikilaw.backend.repository.ClasseProcessualRepository;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import forge.wikilaw.backend.repository.MovimentoProcessualRepository;
import forge.wikilaw.backend.repository.OrgaoJulgadorRepository;
import forge.wikilaw.backend.repository.ProcessoAssuntoRepository;
import forge.wikilaw.backend.repository.ProcessoInstanciaRepository;
import forge.wikilaw.backend.repository.ProcessoRepository;
import forge.wikilaw.backend.repository.TribunalRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataJudRecordProcessorTest {

    @Mock FonteDadosRepository fonteRepository;
    @Mock TribunalRepository tribunalRepository;
    @Mock OrgaoJulgadorRepository orgaoRepository;
    @Mock ClasseProcessualRepository classeRepository;
    @Mock AssuntoProcessualRepository assuntoRepository;
    @Mock ProcessoRepository processoRepository;
    @Mock ProcessoInstanciaRepository instanciaRepository;
    @Mock ProcessoAssuntoRepository processoAssuntoRepository;
    @Mock MovimentoProcessualRepository movimentoRepository;

    private DataJudRecordProcessor processor;
    private FonteDados fonte;
    private Tribunal tribunal;
    private Processo processo;
    private ProcessoInstancia instancia;

    @BeforeEach
    void setUp() {
        processor = new DataJudRecordProcessor(
                fonteRepository, tribunalRepository, orgaoRepository, classeRepository,
                assuntoRepository, processoRepository, instanciaRepository,
                processoAssuntoRepository, movimentoRepository);
        fonte = new FonteDados();
        fonte.setId(1L);
        tribunal = new Tribunal();
        tribunal.setId(2L);
        processo = new Processo();
        processo.setId(3L);
        processo.setNumeroCnj("40017037420268260360");
        instancia = new ProcessoInstancia();
        instancia.setId(4L);
    }

    @Test
    void reusesNaturalKeysWhenTheSameRecordIsImportedAgain() {
        when(fonteRepository.findBySigla("DATAJUD")).thenReturn(Optional.of(fonte));
        when(tribunalRepository.findBySigla("TJSP")).thenReturn(Optional.of(tribunal));
        when(processoRepository.findByNumeroCnj("40017037420268260360"))
                .thenReturn(Optional.of(processo));
        when(instanciaRepository.findByFonteIdAndIdentificadorExterno(
                1L, "TJSP_JE_40017037420268260360"))
                .thenReturn(Optional.of(instancia));
        when(instanciaRepository.saveAndFlush(instancia)).thenReturn(instancia);

        RegistroBruto registro = new RegistroBruto();
        registro.setId(10L);
        NormalizedProcessRecord record = new NormalizedProcessRecord(
                "TJSP_JE_40017037420268260360", "TJSP", "40017037420268260360", "JE",
                null, 0, "4", "Projudi", "Eletrônico", null,
                null, null, List.of(), List.of());

        processor.processar(record, registro, DataJudTribunal.TJSP);
        processor.processar(record, registro, DataJudTribunal.TJSP);

        verify(processoRepository, never()).save(any(Processo.class));
        verify(instanciaRepository, times(2)).saveAndFlush(instancia);
        verify(processoAssuntoRepository, times(2)).deleteByProcessoInstancia(instancia);
        verify(movimentoRepository, times(2)).deleteByProcessoInstancia(instancia);
    }
}
