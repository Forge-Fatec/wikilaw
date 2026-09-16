package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.CargaDados;
import forge.wikilaw.backend.entity.RegistroBruto;
import forge.wikilaw.backend.repository.RegistroBrutoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroBrutoService {

    private final RegistroBrutoRepository repository;

    public RegistroBrutoService(RegistroBrutoRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegistroBruto salvarTextoOriginal(CargaDados carga, String identificador, String payload) {
        return salvarTextoOriginal(carga, identificador, payload, "JSON");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegistroBruto salvarTextoOriginal(CargaDados carga, String identificador, String payload, String formato) {
        RegistroBruto registro = new RegistroBruto();
        registro.setFonte(carga.getFonte());
        registro.setCarga(carga);
        registro.setIdentificadorExterno(identificador);
        registro.setFormatoPayload(formato);
        registro.setDataColeta(OffsetDateTime.now(ZoneOffset.UTC));
        registro.setHashConteudo(sha256(payload));
        registro.setPayloadTexto(payload);
        return repository.save(registro);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegistroBruto confirmarJsonValido(Long registroId, String payload) {
        RegistroBruto registro = repository.findById(registroId)
                .orElseThrow(() -> new IllegalStateException("Registro bruto não encontrado: " + registroId));
        registro.setPayloadJsonb(payload);
        return repository.save(registro);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não disponível", exception);
        }
    }
}
