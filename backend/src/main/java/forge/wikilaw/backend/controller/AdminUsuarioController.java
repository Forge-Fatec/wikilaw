package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.AlterarPerfilRequest;
import forge.wikilaw.backend.dto.CriarUsuarioRequest;
import forge.wikilaw.backend.dto.UsuarioResponse;
import forge.wikilaw.backend.security.UsuarioPrincipal;
import forge.wikilaw.backend.service.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/usuarios")
public class AdminUsuarioController {
    private final UsuarioService service;

    public AdminUsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@Valid @RequestBody CriarUsuarioRequest request) {
        return service.criar(request);
    }

    @PatchMapping("/{id}/perfil")
    public UsuarioResponse alterarPerfil(
            @PathVariable Long id,
            @Valid @RequestBody AlterarPerfilRequest request,
            @AuthenticationPrincipal UsuarioPrincipal administrador) {
        return service.alterarPerfil(id, request, administrador.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioPrincipal administrador) {
        service.excluir(id, administrador.id());
    }
}
