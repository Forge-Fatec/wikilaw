package forge.wikilaw.backend.dto;

import jakarta.validation.constraints.*;

public record SimilaridadeRequest(@Size(max=300) String termo, @Size(max=100000) String texto,
        @Min(1) @Max(200) Integer limite) {
    @AssertTrue(message="Informe a descrição do caso ou o texto de um documento")
    public boolean isConteudoPresente() {
        return termo!=null && !termo.isBlank() || texto!=null && !texto.isBlank();
    }
}
