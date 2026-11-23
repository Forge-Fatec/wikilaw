package forge.wikilaw.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bloqueia temporariamente o login depois de várias senhas erradas seguidas,
 * para impedir que alguém descubra a senha por tentativa e erro.
 *
 * <p>A contagem é por login e endereço de origem: um atacante não consegue
 * bloquear a conta de quem entra de outro endereço. Fica em memória, então
 * recomeça quando o backend reinicia.
 */
@Service
public class LoginAttemptService {
    private static final int LIMITE_DE_REGISTROS = 10_000;

    private final int maxTentativas;
    private final Duration bloqueio;
    private final Clock clock;
    private final Map<String, Tentativas> tentativas = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(
            @Value("${wikilaw.security.login.max-attempts:5}") int maxTentativas,
            @Value("${wikilaw.security.login.lock-duration:15m}") Duration bloqueio) {
        this(maxTentativas, bloqueio, Clock.systemUTC());
    }

    LoginAttemptService(int maxTentativas, Duration bloqueio, Clock clock) {
        if (maxTentativas < 1) {
            throw new IllegalArgumentException("wikilaw.security.login.max-attempts deve ser ao menos 1");
        }
        this.maxTentativas = maxTentativas;
        this.bloqueio = bloqueio;
        this.clock = clock;
    }

    /** Tempo que ainda falta de bloqueio, ou vazio se o login está liberado. */
    public Optional<Duration> bloqueioRestante(String login, String origem) {
        Instant agora = clock.instant();
        Tentativas registro = tentativas.get(chave(login, origem));
        if (registro == null) {
            return Optional.empty();
        }
        if (registro.expirado(agora, bloqueio)) {
            tentativas.remove(chave(login, origem), registro);
            return Optional.empty();
        }
        return registro.bloqueadoAte() == null
                ? Optional.empty()
                : Optional.of(Duration.between(agora, registro.bloqueadoAte()));
    }

    public void registrarFalha(String login, String origem) {
        Instant agora = clock.instant();
        if (tentativas.size() >= LIMITE_DE_REGISTROS) {
            removerExpirados(agora);
        }
        tentativas.compute(chave(login, origem), (chave, atual) -> {
            int falhas = (atual == null || atual.expirado(agora, bloqueio) ? 0 : atual.falhas()) + 1;
            Instant bloqueadoAte = falhas >= maxTentativas ? agora.plus(bloqueio) : null;
            return new Tentativas(falhas, agora, bloqueadoAte);
        });
    }

    public void registrarSucesso(String login, String origem) {
        tentativas.remove(chave(login, origem));
    }

    private void removerExpirados(Instant agora) {
        tentativas.values().removeIf(registro -> registro.expirado(agora, bloqueio));
    }

    private static String chave(String login, String origem) {
        return login.trim().toLowerCase(Locale.ROOT) + "|" + origem;
    }

    /** Falhas esparsas também caducam: só contam as da última janela de bloqueio. */
    private record Tentativas(int falhas, Instant ultimaFalha, Instant bloqueadoAte) {
        boolean expirado(Instant agora, Duration janela) {
            Instant fim = bloqueadoAte != null ? bloqueadoAte : ultimaFalha.plus(janela);
            return !agora.isBefore(fim);
        }
    }
}
