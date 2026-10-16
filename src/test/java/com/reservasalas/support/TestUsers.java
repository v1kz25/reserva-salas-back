package com.reservasalas.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.repository.UserSessionState;
import com.reservasalas.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Usuarios autenticados de prueba y MockMvc con sesión y token CSRF por defecto.
 */
public final class TestUsers {

    /** Usuario normal de prueba. */
    public static final AuthenticatedUser ANA = new AuthenticatedUser(1L, "ana@example.com", "Ana", Role.USER, 0, null);

    /** Administrador de prueba. */
    public static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(2L, "admin@example.com", "Administrador", Role.ADMIN, 0, null);

    private TestUsers() {
    }

    /**
     * MockMvc con la cadena de seguridad aplicada y, en todas las peticiones, el usuario indicado
     * autenticado y un token CSRF válido.
     *
     * @param context contexto web
     * @param user    usuario autenticado por defecto
     * @return el MockMvc
     */
    public static MockMvc mockMvcAs(WebApplicationContext context, AuthenticatedUser user) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user(user)).with(csrf()))
                .build();
    }

    /**
     * Guarda en la base de datos un usuario nuevo con ese rol y devuelve su usuario autenticado,
     * para los tests con la aplicación completa (la sesión solo es válida si el usuario existe).
     *
     * @param users repositorio de usuarios
     * @param role  rol del usuario
     * @return el usuario autenticado equivalente, sin contraseña
     */
    public static AuthenticatedUser persisted(UserRepository users, Role role) {
        User user = users.save(new User(role.name().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID()
                + "@example.com", role == Role.ADMIN ? "Administrador" : "Ana", "hash", role, Instant.EPOCH));
        AuthenticatedUser authenticated = AuthenticatedUser.from(user);
        authenticated.eraseCredentials();
        return authenticated;
    }

    /**
     * Configura un repositorio de usuarios simulado para que toda sesión se considere válida, en
     * los tests de la capa web.
     *
     * @param users repositorio simulado
     */
    public static void acceptAllSessions(UserRepository users) {
        when(users.findSessionStateById(any())).thenReturn(Optional.of(new UserSessionState(true, 0)));
    }
}
