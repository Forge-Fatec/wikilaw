package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.AlterarPerfilRequest;
import forge.wikilaw.backend.dto.CriarUsuarioRequest;
import forge.wikilaw.backend.dto.UsuarioResponse;
import forge.wikilaw.backend.entity.Usuario;
import forge.wikilaw.backend.repository.UsuarioRepository;
import forge.wikilaw.backend.security.UsuarioPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsuarioService {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;

    public UsuarioService(
            UsuarioRepository repository,
            PasswordEncoder passwordEncoder,
            SessionRegistry sessionRegistry) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return repository.findAllByOrderByLoginAsc().stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    @Transactional
    public UsuarioResponse criar(CriarUsuarioRequest request) {
        String login = request.login().trim();
        if (repository.existsByLoginIgnoreCase(login)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ja existe um usuario com esse login");
        }

        Usuario usuario = new Usuario();
        usuario.setLogin(login);
        usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
        usuario.setPerfil(request.perfil());
        usuario.setAtivo(true);
        return UsuarioResponse.from(repository.save(usuario));
    }

    @Transactional
    public UsuarioResponse alterarPerfil(Long id, AlterarPerfilRequest request, Long administradorId) {
        if (id.equals(administradorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voce nao pode alterar a propria permissao");
        }
        Usuario usuario = buscar(id);
        usuario.setPerfil(request.perfil());
        UsuarioResponse response = UsuarioResponse.from(repository.save(usuario));
        encerrarSessoes(id);
        return response;
    }

    @Transactional
    public void excluir(Long id, Long administradorId) {
        if (id.equals(administradorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voce nao pode excluir a propria conta");
        }
        Usuario usuario = buscar(id);
        repository.delete(usuario);
        encerrarSessoes(id);
    }

    private Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado"));
    }

    private void encerrarSessoes(Long usuarioId) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(UsuarioPrincipal.class::isInstance)
                .map(UsuarioPrincipal.class::cast)
                .filter(principal -> principal.id().equals(usuarioId))
                .forEach(principal -> sessionRegistry.getAllSessions(principal, false)
                        .forEach(session -> session.expireNow()));
    }
}
