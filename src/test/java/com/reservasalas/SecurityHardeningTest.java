package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.support.TestHttpClient;
import com.reservasalas.support.TestHttpClient.Reply;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Endurecimiento de la seguridad: consola de H2, credenciales en sesión, cookies Secure y métodos
 * no permitidos en los endpoints de autenticación.
 */
class SecurityHardeningTest {

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "spring.datasource.url=jdbc:h2:mem:sh-prod-${random.uuid}")
    class SinPerfilDev {
        @LocalServerPort
        int port;

        @Test
        void laConsolaDeH2NoEstaDisponible() {
            Reply reply = new TestHttpClient(port).get("/h2-console");

            assertThat(reply.status()).isIn(401, 403, 404);
            assertThat(new TestHttpClient(port).get("/h2-console/login.jsp").status()).isIn(401, 403, 404);
        }

        @Test
        void metodosNoPermitidosEnAuthNoDan200() throws Exception {
            TestHttpClient client = new TestHttpClient(port);
            client.get("/api/auth/csrf");

            Reply getLogin = client.get("/api/auth/login");
            Reply postCsrf = client.send("POST", "/api/auth/csrf", null);

            for (Reply reply : new Reply[] {getLogin, postCsrf}) {
                assertThat(reply.status()).isIn(401, 405);
                assertThat(reply.header("Content-Type").orElse("")).startsWith("application/problem+json");
            }
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "spring.datasource.url=jdbc:h2:mem:sh-dev-${random.uuid}")
    @ActiveProfiles("dev")
    class ConPerfilDev {
        @LocalServerPort
        int port;

        @Test
        void laConsolaDeH2EsAccesible() {
            int status = new TestHttpClient(port).get("/h2-console/login.jsp").status();

            assertThat(status).isBetween(200, 399);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"spring.datasource.url=jdbc:h2:mem:sh-secure-${random.uuid}",
                    "app.cookie-secure=true", "server.servlet.session.cookie.secure=true"})
    class ConCookiesSecure {
        @LocalServerPort
        int port;
        @Autowired
        UserRepository users;
        @Autowired
        PasswordEncoder encoder;

        @Test
        void lasCookiesDeSesionYCsrfLlevanSecure() {
            String email = "sec-" + UUID.randomUUID() + "@example.com";
            users.save(new User(email, "Sec", encoder.encode("clave-de-prueba-1"), Role.USER));
            TestHttpClient client = new TestHttpClient(port);

            Reply csrf = client.get("/api/auth/csrf");
            Reply login = client.send("POST", "/api/auth/login",
                    "{\"email\":\"%s\",\"password\":\"clave-de-prueba-1\"}".formatted(email));

            assertThat(csrf.setCookie("XSRF-TOKEN").orElseThrow()).containsIgnoringCase("Secure");
            assertThat(login.status()).isEqualTo(200);
            assertThat(login.setCookie("JSESSIONID").orElseThrow()).containsIgnoringCase("Secure");
            assertThat(login.setCookie("XSRF-TOKEN").orElseThrow()).containsIgnoringCase("Secure");
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sh-ctx-${random.uuid}")
    class PrincipalEnSesion {
        @Autowired
        WebApplicationContext context;
        @Autowired
        UserRepository users;
        @Autowired
        PasswordEncoder encoder;

        @Test
        void elPrincipalGuardadoEnLaSesionNoConservaElHash() throws Exception {
            String email = "ctx-" + UUID.randomUUID() + "@example.com";
            users.save(new User(email, "Ctx", encoder.encode("clave-de-prueba-1"), Role.USER));
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"clave-de-prueba-1\"}".formatted(email)))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            HttpSession session = result.getRequest().getSession(false);
            assertThat(session).isNotNull();
            SecurityContext stored = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
            assertThat(stored).isNotNull();
            AuthenticatedUser principal = (AuthenticatedUser) stored.getAuthentication().getPrincipal();
            assertThat(principal.getPassword()).isNull();
            assertThat(stored.getAuthentication().getCredentials()).isNull();
        }

        @Test
        void eraseCredentialsBorraElHash() {
            AuthenticatedUser user = new AuthenticatedUser(1L, "a@example.com", "A", Role.USER, "$2a$10$hash");

            user.eraseCredentials();

            assertThat(user.getPassword()).isNull();
        }
    }
}
