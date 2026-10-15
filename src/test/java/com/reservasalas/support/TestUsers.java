package com.reservasalas.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.reservasalas.entity.Role;
import com.reservasalas.security.AuthenticatedUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Usuarios autenticados de prueba y MockMvc con sesión y token CSRF por defecto.
 */
public final class TestUsers {

    /** Usuario normal de prueba. */
    public static final AuthenticatedUser ANA = new AuthenticatedUser(1L, "ana@example.com", "Ana", Role.USER, null);

    /** Administrador de prueba. */
    public static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(2L, "admin@example.com", "Administrador", Role.ADMIN, null);

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
}
