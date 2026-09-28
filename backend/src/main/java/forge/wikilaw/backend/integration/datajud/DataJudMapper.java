package forge.wikilaw.backend.integration.datajud;

import forge.wikilaw.backend.integration.NormalizedProcessRecord;
import forge.wikilaw.backend.integration.NormalizedProcessRecord.NormalizedClassificacao;
import forge.wikilaw.backend.integration.NormalizedProcessRecord.NormalizedMovimento;
import forge.wikilaw.backend.integration.NormalizedProcessRecord.NormalizedOrgao;
import forge.wikilaw.backend.integration.datajud.dto.DataJudCodeNameDto;
import forge.wikilaw.backend.integration.datajud.dto.DataJudHitDto;
import forge.wikilaw.backend.integration.datajud.dto.DataJudMovimentoDto;
import forge.wikilaw.backend.integration.datajud.dto.DataJudOrgaoDto;
import forge.wikilaw.backend.integration.datajud.dto.DataJudSourceDto;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class DataJudMapper {

    private static final DateTimeFormatter COMPACT_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ObjectMapper objectMapper;

    public DataJudMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedProcessRecord map(DataJudHitDto hit, DataJudTribunal requestedTribunal) {
        if (hit == null || hit.source() == null) {
            throw new IllegalArgumentException("Hit do DataJud sem _source");
        }
        DataJudSourceDto source = hit.source();
        String externalId = firstNonBlank(source.id(), hit.id());
        if (externalId == null) {
            throw new IllegalArgumentException("Hit do DataJud sem identificador externo");
        }

        String sourceTribunal = required(source.tribunal(), "tribunal").toUpperCase(Locale.ROOT);
        if (!requestedTribunal.name().equals(sourceTribunal)) {
            throw new IllegalArgumentException("Tribunal da resposta não corresponde ao alias consultado");
        }

        String numeroCnj = digitsOnly(required(source.numeroProcesso(), "numeroProcesso"));
        if (numeroCnj.length() != 20) {
            throw new IllegalArgumentException("numeroProcesso não contém 20 dígitos");
        }

        return new NormalizedProcessRecord(
                externalId,
                sourceTribunal,
                numeroCnj,
                source.grau(),
                parseDate(source.dataAjuizamento()),
                source.nivelSigilo(),
                codeAsString(source.sistema()),
                nameOf(source.sistema()),
                nameOf(source.formato()),
                parseDate(firstNonBlank(source.dataHoraUltimaAtualizacao(), source.timestamp())),
                mapOrgao(source.orgaoJulgador()),
                mapClassification(source.classe()),
                mapAssuntos(source.assuntos()),
                mapMovimentos(source.movimentos()));
    }

    private List<NormalizedClassificacao> mapAssuntos(List<DataJudCodeNameDto> assuntos) {
        if (assuntos == null) {
            return List.of();
        }
        Map<Long, NormalizedClassificacao> unique = new LinkedHashMap<>();
        assuntos.stream()
                .map(this::mapClassification)
                .filter(Objects::nonNull)
                .forEach(assunto -> unique.putIfAbsent(assunto.codigo(), assunto));
        return List.copyOf(unique.values());
    }

    private List<NormalizedMovimento> mapMovimentos(List<DataJudMovimentoDto> movimentos) {
        if (movimentos == null) {
            return List.of();
        }
        return movimentos.stream().filter(Objects::nonNull).map(movimento -> new NormalizedMovimento(
                movimento.codigo(),
                movimento.nome(),
                parseDate(movimento.dataHora()),
                toJson(movimento),
                mapOrgao(movimento.orgaoJulgador()))).toList();
    }

    private String toJson(DataJudMovimentoDto movimento) {
        if (movimento.complementosTabelados() == null || movimento.complementosTabelados().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(movimento.complementosTabelados());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Complementos de movimento não puderam ser serializados", exception);
        }
    }

    private NormalizedClassificacao mapClassification(DataJudCodeNameDto value) {
        if (value == null || value.codigo() == null || isBlank(value.nome())) {
            return null;
        }
        return new NormalizedClassificacao(value.codigo(), value.nome().trim());
    }

    private NormalizedOrgao mapOrgao(DataJudOrgaoDto value) {
        if (value == null || isBlank(value.codigo()) || isBlank(value.nome())) {
            return null;
        }
        return new NormalizedOrgao(value.codigo().trim(), value.nome().trim(), value.codigoMunicipioIBGE());
    }

    private OffsetDateTime parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.matches("\\d{14}")) {
                return LocalDateTime.parse(normalized, COMPACT_DATE_TIME).atOffset(ZoneOffset.UTC);
            }
            if (normalized.matches("\\d{13}")) {
                return Instant.ofEpochMilli(Long.parseLong(normalized)).atOffset(ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(normalized);
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw new IllegalArgumentException("Data DataJud inválida: " + normalized, exception);
        }
    }

    private String codeAsString(DataJudCodeNameDto value) {
        return value == null || value.codigo() == null ? null : value.codigo().toString();
    }

    private String nameOf(DataJudCodeNameDto value) {
        return value == null ? null : value.nome();
    }

    private String required(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Campo obrigatório ausente no DataJud: " + field);
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? (isBlank(second) ? null : second.trim()) : first.trim();
    }

    private String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
