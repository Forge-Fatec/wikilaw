package forge.wikilaw.backend.security;

import forge.wikilaw.backend.entity.Usuario;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UsuarioPrincipal(
        Long id,
        String username,
        String password,
        boolean enabled,
        Collection<? extends GrantedAuthority> authorities) implements UserDetails {

    public static UsuarioPrincipal from(Usuario usuario) {
        return new UsuarioPrincipal(
                usuario.getId(),
                usuario.getLogin(),
                usuario.getSenhaHash(),
                usuario.isAtivo(),
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
