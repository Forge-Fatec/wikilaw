package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.LoginRequest;
import forge.wikilaw.backend.dto.UsuarioResponse;
import forge.wikilaw.backend.repository.UsuarioRepository;
import forge.wikilaw.backend.security.LoginAttemptService;
import forge.wikilaw.backend.security.UsuarioPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {
    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository repository;
    private final SessionRegistry sessionRegistry;
    private final LoginAttemptService tentativas;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AutenticacaoController(
            AuthenticationManager authenticationManager,
            UsuarioRepository repository,
            SessionRegistry sessionRegistry,
            LoginAttemptService tentativas) {
        this.authenticationManager = authenticationManager;
        this.repository = repository;
        this.sessionRegistry = sessionRegistry;
        this.tentativas = tentativas;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/login")
    public UsuarioResponse login(
            @Valid @RequestBody LoginRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        String login = requestBody.login().trim();
        String origem = request.getRemoteAddr();
        // Bloqueado, nem confere a senha: a resposta não revela se ela estaria certa.
        tentativas.bloqueioRestante(login, origem).ifPresent(restante -> {
            response.setHeader("Retry-After", String.valueOf(segundos(restante)));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Tente novamente em " + minutos(restante) + ".");
        });
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login, requestBody.senha()));
            tentativas.registrarSucesso(login, origem);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }
            securityContextRepository.saveContext(context, request, response);
            UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
            String sessionId = request.getSession().getId();
            sessionRegistry.removeSessionInformation(sessionId);
            sessionRegistry.registerNewSession(sessionId, principal);
            return usuario(principal.id());
        } catch (AuthenticationException exception) {
            tentativas.registrarFalha(login, origem);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login ou senha invalidos");
        }
    }

    @GetMapping("/me")
    public UsuarioResponse me(Authentication authentication) {
        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        return usuario(principal.id());
    }

    @PostMapping("/logout")
    public void logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (request.getSession(false) != null) {
            sessionRegistry.removeSessionInformation(request.getSession(false).getId());
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    /** Arredonda para cima: o cliente nunca deve voltar antes do fim do bloqueio. */
    private static long segundos(Duration restante) {
        return Math.max(1, (restante.toMillis() + 999) / 1000);
    }

    private static String minutos(Duration restante) {
        long minutos = (segundos(restante) + 59) / 60;
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }

    private UsuarioResponse usuario(Long id) {
        return repository.findById(id)
                .map(UsuarioResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
