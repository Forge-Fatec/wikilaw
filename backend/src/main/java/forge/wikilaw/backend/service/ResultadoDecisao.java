package forge.wikilaw.backend.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resultado do recurso identificado a partir do texto livre do dispositivo
 * ("decisao") informado pela fonte.
 */
public enum ResultadoDecisao {
    PROVIDO("Provido"),
    PARCIAL("Parcialmente provido"),
    DESPROVIDO("Desprovido"),
    INDEFINIDO("Não identificado");

    // A ordem importa: "desprovido" e "improcedente" contêm "provido" e "procedente".
    private static final Pattern PADROES_DESPROVIDO = Pattern.compile(
            "\\b(desprovid|improvid|nao provid|neg[\\w-]* (o )?provimento|improceden)");
    private static final Pattern PADROES_PARCIAL = Pattern.compile(
            "\\b(parcial(mente)? provid|provid\\w* em parte|parcial provimento|provimento parcial"
                    + "|parcialmente procedente|procedente em parte)");
    private static final Pattern PADROES_PROVIDO = Pattern.compile(
            "\\b(provid|(deu|deram|dar|dou|dado|da-se) provimento|procedente)");

    private final String rotulo;

    ResultadoDecisao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    public boolean favoravel() {
        return this == PROVIDO || this == PARCIAL;
    }

    /** Resultado cujo rótulo (o mesmo exibido no gráfico) é o informado. */
    public static Optional<ResultadoDecisao> doRotulo(String rotulo) {
        if (rotulo == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(resultado -> resultado.rotulo.equalsIgnoreCase(rotulo.trim()))
                .findFirst();
    }

    public static ResultadoDecisao classificar(String decisao) {
        if (decisao == null || decisao.isBlank()) {
            return INDEFINIDO;
        }

        String texto = normalizar(decisao);
        if (PADROES_DESPROVIDO.matcher(texto).find()) return DESPROVIDO;
        if (PADROES_PARCIAL.matcher(texto).find()) return PARCIAL;
        if (PADROES_PROVIDO.matcher(texto).find()) return PROVIDO;
        return INDEFINIDO;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
