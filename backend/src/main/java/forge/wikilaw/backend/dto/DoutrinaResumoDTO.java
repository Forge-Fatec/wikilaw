package forge.wikilaw.backend.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Formato de saída da API para um documento doutrinário.
 * Pensado pra bater com o card de resultado da tela (badge "DOUTRINA",
 * título, resumo, data e fonte original).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoutrinaResumoDTO {

    private Long idDocumento;
    private String titulo;
    private String resumo;
    private LocalDate dataPublicacao;
    private String urlOriginal;
}