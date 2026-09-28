package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.AssuntoProcessual;
import forge.wikilaw.backend.entity.ClasseProcessual;
import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.entity.MovimentoProcessual;
import forge.wikilaw.backend.entity.OrgaoJulgador;
import forge.wikilaw.backend.entity.Processo;
import forge.wikilaw.backend.entity.ProcessoAssunto;
import forge.wikilaw.backend.entity.ProcessoAssuntoId;
import forge.wikilaw.backend.entity.ProcessoInstancia;
import forge.wikilaw.backend.entity.RegistroBruto;
import forge.wikilaw.backend.entity.Tribunal;
import forge.wikilaw.backend.integration.NormalizedProcessRecord;
import forge.wikilaw.backend.integration.NormalizedProcessRecord.NormalizedClassificacao;
import forge.wikilaw.backend.integration.NormalizedProcessRecord.NormalizedMovimento;
import forge.wikilaw.backend.integration.NormalizedProcessRecord.NormalizedOrgao;
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
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataJudRecordProcessor {

    private final FonteDadosRepository fonteRepository;
    private final TribunalRepository tribunalRepository;
    private final OrgaoJulgadorRepository orgaoRepository;
    private final ClasseProcessualRepository classeRepository;
    private final AssuntoProcessualRepository assuntoRepository;
    private final ProcessoRepository processoRepository;
    private final ProcessoInstanciaRepository instanciaRepository;
    private final ProcessoAssuntoRepository processoAssuntoRepository;
    private final MovimentoProcessualRepository movimentoRepository;

    public DataJudRecordProcessor(
            FonteDadosRepository fonteRepository,
            TribunalRepository tribunalRepository,
            OrgaoJulgadorRepository orgaoRepository,
            ClasseProcessualRepository classeRepository,
            AssuntoProcessualRepository assuntoRepository,
            ProcessoRepository processoRepository,
            ProcessoInstanciaRepository instanciaRepository,
            ProcessoAssuntoRepository processoAssuntoRepository,
            MovimentoProcessualRepository movimentoRepository) {
        this.fonteRepository = fonteRepository;
        this.tribunalRepository = tribunalRepository;
        this.orgaoRepository = orgaoRepository;
        this.classeRepository = classeRepository;
        this.assuntoRepository = assuntoRepository;
        this.processoRepository = processoRepository;
        this.instanciaRepository = instanciaRepository;
        this.processoAssuntoRepository = processoAssuntoRepository;
        this.movimentoRepository = movimentoRepository;
    }

    @Transactional
    public ProcessoInstancia processar(
            NormalizedProcessRecord record,
            RegistroBruto registroBruto,
            DataJudTribunal tribunalConfig) {
        FonteDados fonte = fonteRepository.findBySigla("DATAJUD")
                .orElseThrow(() -> new IllegalStateException("Fonte DATAJUD não cadastrada"));
        Tribunal tribunal = tribunalRepository.findBySigla(record.tribunalSigla())
                .orElseGet(() -> criarTribunal(tribunalConfig));
        Processo processo = processoRepository.findByNumeroCnj(record.numeroCnj())
                .orElseGet(() -> criarProcesso(record.numeroCnj()));
        OrgaoJulgador orgaoAtual = upsertOrgao(tribunal, record.orgaoJulgadorAtual());
        ClasseProcessual classe = upsertClasse(record.classe());

        ProcessoInstancia instancia = instanciaRepository
                .findByFonteIdAndIdentificadorExterno(fonte.getId(), record.identificadorExterno())
                .orElseGet(ProcessoInstancia::new);
        instancia.setProcesso(processo);
        instancia.setFonte(fonte);
        instancia.setRegistroBruto(registroBruto);
        instancia.setTribunal(tribunal);
        instancia.setOrgaoJulgadorAtual(orgaoAtual);
        instancia.setClasseProcessual(classe);
        instancia.setIdentificadorExterno(record.identificadorExterno());
        instancia.setGrau(record.grau());
        instancia.setDataAjuizamento(record.dataAjuizamento());
        instancia.setNivelSigilo(record.nivelSigilo());
        instancia.setCodigoSistema(record.codigoSistema());
        instancia.setNomeSistema(record.nomeSistema());
        instancia.setFormatoProcesso(record.formatoProcesso());
        instancia.setDataUltimaAtualizacaoFonte(record.dataUltimaAtualizacaoFonte());
        instancia = instanciaRepository.saveAndFlush(instancia);

        substituirAssuntos(instancia, record.assuntos());
        substituirMovimentos(instancia, tribunal, record.movimentos());
        return instancia;
    }

    private Processo criarProcesso(String numeroCnj) {
        Processo processo = new Processo();
        processo.setNumeroCnj(numeroCnj);
        return processoRepository.save(processo);
    }

    private ClasseProcessual upsertClasse(NormalizedClassificacao value) {
        if (value == null) {
            return null;
        }
        ClasseProcessual classe = classeRepository.findByCodigoCnj(value.codigo())
                .orElseGet(ClasseProcessual::new);
        classe.setCodigoCnj(value.codigo());
        classe.setNome(value.nome());
        return classeRepository.save(classe);
    }

    private AssuntoProcessual upsertAssunto(NormalizedClassificacao value) {
        AssuntoProcessual assunto = assuntoRepository.findByCodigoCnj(value.codigo())
                .orElseGet(AssuntoProcessual::new);
        assunto.setCodigoCnj(value.codigo());
        assunto.setNome(value.nome());
        return assuntoRepository.save(assunto);
    }

    private OrgaoJulgador upsertOrgao(Tribunal tribunal, NormalizedOrgao value) {
        if (value == null) {
            return null;
        }
        OrgaoJulgador orgao = orgaoRepository
                .findByTribunalIdAndCodigoExterno(tribunal.getId(), value.codigo())
                .orElseGet(OrgaoJulgador::new);
        orgao.setTribunal(tribunal);
        orgao.setCodigoExterno(value.codigo());
        orgao.setNome(value.nome());
        if (value.codigoMunicipioIbge() != null) {
            orgao.setCodigoMunicipioIbge(value.codigoMunicipioIbge());
        }
        return orgaoRepository.save(orgao);
    }

    private void substituirAssuntos(
            ProcessoInstancia instancia,
            List<NormalizedClassificacao> assuntosNormalizados) {
        processoAssuntoRepository.deleteByProcessoInstancia(instancia);
        processoAssuntoRepository.flush();
        List<ProcessoAssunto> relacionamentos = new ArrayList<>();
        for (NormalizedClassificacao value : assuntosNormalizados) {
            AssuntoProcessual assunto = upsertAssunto(value);
            ProcessoAssunto relacionamento = new ProcessoAssunto();
            relacionamento.setId(new ProcessoAssuntoId(instancia.getId(), assunto.getId()));
            relacionamento.setProcessoInstancia(instancia);
            relacionamento.setAssuntoProcessual(assunto);
            relacionamento.setPrincipal(false);
            relacionamentos.add(relacionamento);
        }
        processoAssuntoRepository.saveAll(relacionamentos);
    }

    private void substituirMovimentos(
            ProcessoInstancia instancia,
            Tribunal tribunal,
            List<NormalizedMovimento> movimentosNormalizados) {
        movimentoRepository.deleteByProcessoInstancia(instancia);
        movimentoRepository.flush();
        List<MovimentoProcessual> movimentos = new ArrayList<>();
        for (NormalizedMovimento value : movimentosNormalizados) {
            MovimentoProcessual movimento = new MovimentoProcessual();
            movimento.setProcessoInstancia(instancia);
            movimento.setOrgaoJulgador(upsertOrgao(tribunal, value.orgaoJulgador()));
            movimento.setCodigoCnj(value.codigo());
            movimento.setNome(value.nome());
            movimento.setDataHora(value.dataHora());
            movimento.setComplementosJsonb(value.complementosJson());
            movimentos.add(movimento);
        }
        movimentoRepository.saveAll(movimentos);
    }

    private Tribunal criarTribunal(DataJudTribunal config) {
        Tribunal tribunal = new Tribunal();
        tribunal.setSigla(config.name());
        tribunal.setNome(config.nome());
        tribunal.setUf(config.uf());
        tribunal.setRamoJustica("JUSTIÇA ESTADUAL");
        tribunal.setEsfera("ESTADUAL");
        return tribunalRepository.save(tribunal);
    }
}
