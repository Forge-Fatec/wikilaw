package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.CargaDados;
import forge.wikilaw.backend.entity.CargaStatus;
import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.repository.CargaDadosRepository;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CargaDadosService {

    private final CargaDadosRepository cargaRepository;
    private final FonteDadosRepository fonteRepository;

    public CargaDadosService(CargaDadosRepository cargaRepository, FonteDadosRepository fonteRepository) {
        this.cargaRepository = cargaRepository;
        this.fonteRepository = fonteRepository;
    }

    @Transactional
    public CargaDados iniciar(String fonteSigla) {
        FonteDados fonte = fonteRepository.findBySigla(fonteSigla)
                .orElseThrow(() -> new IllegalStateException("Fonte não cadastrada: " + fonteSigla));
        if (!fonte.isAtivo()) {
            throw new IllegalStateException("Fonte desativada: " + fonteSigla);
        }
        CargaDados carga = new CargaDados();
        carga.setFonte(fonte);
        carga.setDataInicio(OffsetDateTime.now(ZoneOffset.UTC));
        carga.setStatus(CargaStatus.EM_EXECUCAO);
        return cargaRepository.save(carga);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CargaDados concluir(
            Long cargaId,
            int recebidos,
            int processados,
            int erros,
            String mensagemErro,
            boolean falhaFatal) {
        CargaDados carga = cargaRepository.findById(cargaId)
                .orElseThrow(() -> new IllegalStateException("Carga não encontrada: " + cargaId));
        carga.setDataFim(OffsetDateTime.now(ZoneOffset.UTC));
        carga.setQuantidadeRecebida(recebidos);
        carga.setQuantidadeProcessada(processados);
        carga.setQuantidadeErro(erros);
        carga.setMensagemErro(limit(mensagemErro, 8000));
        carga.setStatus(falhaFatal
                ? CargaStatus.FALHA
                : erros > 0 ? CargaStatus.CONCLUIDA_COM_ERROS : CargaStatus.CONCLUIDA);
        return cargaRepository.save(carga);
    }

    private String limit(String value, int length) {
        if (value == null || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }
}
