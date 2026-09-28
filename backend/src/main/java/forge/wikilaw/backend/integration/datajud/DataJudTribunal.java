package forge.wikilaw.backend.integration.datajud;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum DataJudTribunal {
    TJSP("tjsp", "Tribunal de Justiça de São Paulo", "SP"),
    TJRJ("tjrj", "Tribunal de Justiça do Rio de Janeiro", "RJ"),
    TJMG("tjmg", "Tribunal de Justiça de Minas Gerais", "MG");

    private final String alias;
    private final String nome;
    private final String uf;

    DataJudTribunal(String alias, String nome, String uf) {
        this.alias = alias;
        this.nome = nome;
        this.uf = uf;
    }

    public String alias() {
        return alias;
    }

    public String nome() {
        return nome;
    }

    public String uf() {
        return uf;
    }

    @JsonCreator
    public static DataJudTribunal from(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return name();
    }
}
