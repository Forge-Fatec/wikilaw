package forge.wikilaw.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private static final String ORIGEM = "10.0.0.1";

    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-11-22T12:00:00Z"));
    private final LoginAttemptService service = new LoginAttemptService(3, Duration.ofMinutes(15), relogio);

    @Test
    void liberaLoginAteAtingirOLimiteDeFalhas() {
        service.registrarFalha("Admin", ORIGEM);
        service.registrarFalha("Admin", ORIGEM);

        assertThat(service.bloqueioRestante("Admin", ORIGEM)).isEmpty();
    }

    @Test
    void bloqueiaNaFalhaQueAtingeOLimiteEIgnoraMaiusculasEEspacos() {
        service.registrarFalha("Admin", ORIGEM);
        service.registrarFalha(" admin ", ORIGEM);
        service.registrarFalha("ADMIN", ORIGEM);

        assertThat(service.bloqueioRestante("admin", ORIGEM)).contains(Duration.ofMinutes(15));
        relogio.avancar(Duration.ofMinutes(10));
        assertThat(service.bloqueioRestante("Admin", ORIGEM)).contains(Duration.ofMinutes(5));
    }

    @Test
    void liberaQuandoOBloqueioTerminaEReiniciaAContagem() {
        bloquear("Admin", ORIGEM);

        relogio.avancar(Duration.ofMinutes(15));

        assertThat(service.bloqueioRestante("Admin", ORIGEM)).isEmpty();
        service.registrarFalha("Admin", ORIGEM);
        assertThat(service.bloqueioRestante("Admin", ORIGEM)).isEmpty();
    }

    @Test
    void falhasAntigasCaducamSemBloquear() {
        service.registrarFalha("Admin", ORIGEM);
        service.registrarFalha("Admin", ORIGEM);

        relogio.avancar(Duration.ofMinutes(16));
        service.registrarFalha("Admin", ORIGEM);

        assertThat(service.bloqueioRestante("Admin", ORIGEM)).isEmpty();
    }

    @Test
    void loginCertoZeraAsFalhas() {
        service.registrarFalha("Admin", ORIGEM);
        service.registrarFalha("Admin", ORIGEM);

        service.registrarSucesso("Admin", ORIGEM);
        service.registrarFalha("Admin", ORIGEM);
        service.registrarFalha("Admin", ORIGEM);

        assertThat(service.bloqueioRestante("Admin", ORIGEM)).isEmpty();
    }

    @Test
    void bloqueioNaoAfetaOutroLoginNemOutraOrigem() {
        bloquear("Admin", ORIGEM);

        assertThat(service.bloqueioRestante("Admin", ORIGEM)).isPresent();
        assertThat(service.bloqueioRestante("maria", ORIGEM)).isEmpty();
        assertThat(service.bloqueioRestante("Admin", "10.0.0.2")).isEmpty();
    }

    @Test
    void exigeAoMenosUmaTentativa() {
        assertThatThrownBy(() -> new LoginAttemptService(0, Duration.ofMinutes(1), relogio))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void bloquear(String login, String origem) {
        for (int falha = 0; falha < 3; falha++) {
            service.registrarFalha(login, origem);
        }
    }

    private static final class RelogioAjustavel extends Clock {
        private Instant agora;

        RelogioAjustavel(Instant agora) {
            this.agora = agora;
        }

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
