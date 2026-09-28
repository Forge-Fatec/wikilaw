package forge.wikilaw.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wikilaw.bootstrap.jurisprudencia")
public class JurisprudenciaBootstrapProperties {

    private boolean enabled;

    @NotBlank
    @Size(max = 300)
    private String termo = "dano moral";

    @Min(1)
    @Max(20)
    private int paginas = 3;

    @Min(1)
    @Max(100)
    private int tamanhoPagina = 20;

    private boolean somenteSeVazio;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTermo() {
        return termo;
    }

    public void setTermo(String termo) {
        this.termo = termo;
    }

    public int getPaginas() {
        return paginas;
    }

    public void setPaginas(int paginas) {
        this.paginas = paginas;
    }

    public int getTamanhoPagina() {
        return tamanhoPagina;
    }

    public void setTamanhoPagina(int tamanhoPagina) {
        this.tamanhoPagina = tamanhoPagina;
    }

    public boolean isSomenteSeVazio() {
        return somenteSeVazio;
    }

    public void setSomenteSeVazio(boolean somenteSeVazio) {
        this.somenteSeVazio = somenteSeVazio;
    }
}
