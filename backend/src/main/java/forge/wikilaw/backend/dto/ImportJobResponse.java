package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.entity.ImportJob;
import java.time.OffsetDateTime;

public record ImportJobResponse(Long idTarefa, String fonte, ImportJob.Status status, long paginas,
        int maximoPaginas, long recebidos, long processados, long erros, Long ultimaCarga,
        String mensagem, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm, boolean ateEsgotar) {
    public static ImportJobResponse from(ImportJob j) {
        return new ImportJobResponse(j.getId(), j.getFonte(), j.getStatus(), j.getPaginas(),
                j.getMaximoPaginas(), j.getRecebidos(), j.getProcessados(), j.getErros(),
                j.getUltimaCarga(), j.getMensagem(), j.getCriadoEm(), j.getAtualizadoEm(), j.isAteEsgotar());
    }
}
