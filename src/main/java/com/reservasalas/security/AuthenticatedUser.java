package com.reservasalas.security;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado que se guarda en la sesión. Tras el login, Spring Security borra el hash de
 * la contraseña ({@link #eraseCredentials()}), de modo que la sesión no lo conserva.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String name;
    private final Role role;
    private String password;

    /**
     * Crea el usuario autenticado.
     *
     * @param id       identificador del usuario
     * @param email    email normalizado
     * @param name     nombre visible
     * @param role     rol del usuario
     * @param password hash de la contraseña, o {@code null} si ya se ha borrado
     */
    public AuthenticatedUser(Long id, String email, String name, Role role, String password) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
        this.password = password;
    }

    /**
     * Construye el usuario autenticado a partir de la entidad, con el hash de su contraseña para
     * poder comprobarla.
     *
     * @param user usuario persistido
     * @return el usuario autenticado equivalente
     */
    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                user.getPasswordHash());
    }

    /**
     * Identificador del usuario.
     *
     * @return el identificador
     */
    public Long getId() {
        return id;
    }

    /**
     * Email normalizado del usuario.
     *
     * @return el email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Nombre visible del usuario.
     *
     * @return el nombre
     */
    public String getName() {
        return name;
    }

    /**
     * Rol del usuario.
     *
     * @return el rol
     */
    public Role getRole() {
        return role;
    }

    /**
     * Indica si el usuario es administrador.
     *
     * @return {@code true} si su rol es {@link Role#ADMIN}
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AuthenticatedUser other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + ", role=" + role + "]";
    }
}
