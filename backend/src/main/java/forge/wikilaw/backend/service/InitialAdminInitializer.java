package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.PerfilUsuario;
import forge.wikilaw.backend.entity.Usuario;
import forge.wikilaw.backend.repository.UsuarioRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialAdminInitializer implements ApplicationRunner {
    /** Senha de desenvolvimento, pública no repositório: nunca deve chegar a produção. */
    static final String SENHA_PADRAO = "Admin 123";

    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String login;
    private final String password;

    public InitialAdminInitializer(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            @Value("${wikilaw.security.initial-admin.login:Admin}") String login,
            @Value("${wikilaw.security.initial-admin.password:" + SENHA_PADRAO + "}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.login = login;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<Usuario> existente = repository.findByLoginIgnoreCase(login.trim());
        if (existente.isPresent()) {
            if (passwordEncoder.matches(SENHA_PADRAO, existente.get().getSenhaHash())) {
                log.warn("O administrador '{}' ainda usa a senha padrao publica. Crie outro administrador "
                        + "com senha forte e exclua este, ou recrie-o definindo WIKILAW_INITIAL_ADMIN_PASSWORD.",
                        existente.get().getLogin());
            }
            return;
        }

        Usuario admin = new Usuario();
        admin.setLogin(login.trim());
        admin.setSenhaHash(passwordEncoder.encode(password));
        admin.setPerfil(PerfilUsuario.ADMIN);
        admin.setAtivo(true);
        repository.save(admin);
        log.info("Usuario administrador inicial '{}' criado", admin.getLogin());
        if (SENHA_PADRAO.equals(password)) {
            log.warn("O administrador inicial foi criado com a senha padrao publica. "
                    + "Defina WIKILAW_INITIAL_ADMIN_PASSWORD fora do ambiente de desenvolvimento.");
        }
    }
}
