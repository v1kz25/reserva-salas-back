package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.support.TestHttpClient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Con servidor real, una cabecera {@code X-Forwarded-For} falsificada no cambia la clave del límite
 * de intentos: se usa la IP de la conexión.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:login-xff-${random.uuid}",
        "app.login.max-attempts=5"})
class LoginForwardedForTest {

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    @Test
    void cabecerasXForwardedForDistintasNoEvitanElBloqueo() {
        String email = "ana-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        users.save(new User(email, "Ana", encoder.encode("clave-de-prueba-1"), Role.USER, Instant.EPOCH));
        TestHttpClient client = new TestHttpClient(port);
        assertThat(client.get("/api/auth/csrf").status()).isEqualTo(204);

        for (int i = 0; i < 5; i++) {
            var reply = login(client, email, "incorrecta", "203.0.113." + i);
            assertThat(reply.status()).isEqualTo(401);
        }

        var blocked = login(client, email, "clave-de-prueba-1", "203.0.113.99");
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.header("Retry-After")).isPresent();
    }

    private static TestHttpClient.Reply login(TestHttpClient client, String email, String password, String xff) {
        return client.send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password),
                client.cookie("XSRF-TOKEN"), Map.of("X-Forwarded-For", xff));
    }
}
