package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.support.TestHttpClient;
import com.reservasalas.support.TestHttpClient.Reply;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Regla del último administrador activo, versión de sesión y cambios simultáneos sobre un mismo
 * usuario. Usa su propia base de datos y la vacía antes de cada test, porque la regla del último
 * administrador depende de todos los administradores existentes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:usuarios-conc-${random.uuid}")
class GestionUsuariosConcurrenciaIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba-1";
    private static final int ROUNDS = 12;

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository users;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private PasswordEncoder encoder;

    private String suffix;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        users.deleteAll();
        suffix = UUID.randomUUID().toString().substring(0, 8);
    }

    private User save(String name, Role role) {
        return users.save(new User(name.toLowerCase() + "-" + suffix + "@example.com", name,
                encoder.encode(PASSWORD), role, Instant.EPOCH));
    }

    private TestHttpClient loggedAs(User user) {
        TestHttpClient client = new TestHttpClient(port);
        assertThat(client.get("/api/auth/csrf").status()).isEqualTo(204);
        Reply reply = client.send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user.getEmail(), PASSWORD));
        assertThat(reply.status()).isEqualTo(200);
        return client;
    }

    private static String change(String name, String role, boolean active) {
        return "{\"nombre\":\"%s\",\"rol\":\"%s\",\"activo\":%s}".formatted(name, role, active);
    }

    private static String password(String value) {
        return "{\"password\":\"%s\"}".formatted(value);
    }

    private long activeAdmins() {
        return users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN && u.isActive()).count();
    }

    /** Ejecuta las tareas a la vez (todas esperan a la misma señal) y devuelve sus resultados. */
    private static List<Reply> concurrently(List<Callable<Reply>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Reply>> futures = new ArrayList<>();
            for (Callable<Reply> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Reply> replies = new ArrayList<>();
            for (Future<Reply> future : futures) {
                replies.add(future.get(60, TimeUnit.SECONDS));
            }
            return replies;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- último administrador ----

    @Test
    void conDosAdminsUnoDegradaAlOtroYDespuesNadieDejaAlQuedadoSinRol() {
        User a = save("Alfa", Role.ADMIN);
        User b = save("Beta", Role.ADMIN);
        TestHttpClient clientA = loggedAs(a);

        assertThat(clientA.send("PUT", "/api/usuarios/" + b.getId(), change("Beta", "USER", true)).status())
                .isEqualTo(200);

        assertThat(clientA.send("PUT", "/api/usuarios/" + a.getId(), change("Alfa", "USER", true)).status())
                .isEqualTo(409);
        assertThat(clientA.send("PUT", "/api/usuarios/" + a.getId(), change("Alfa", "ADMIN", false)).status())
                .isEqualTo(409);
        assertThat(activeAdmins()).isEqualTo(1);
        // B, ya USER, no puede actuar sobre A
        assertThat(loggedAs(b).send("PUT", "/api/usuarios/" + a.getId(), change("Alfa", "USER", true)).status())
                .isEqualTo(403);
        assertThat(activeAdmins()).isEqualTo(1);
    }

    @Test
    void desactivarAlUnicoOtroAdminSiendoAdminEsCorrecto() {
        User a = save("Alfa", Role.ADMIN);
        User b = save("Beta", Role.ADMIN);

        Reply reply = loggedAs(a).send("PUT", "/api/usuarios/" + b.getId(), change("Beta", "ADMIN", false));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(activeAdmins()).isEqualTo(1);
    }

    @Test
    void dosAdminsQueSeDegradanMutuamenteAlMismoTiempoNuncaDejanLaAppSinAdmins() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            users.deleteAll();
            User a = save("Alfa", Role.ADMIN);
            User b = save("Beta", Role.ADMIN);
            TestHttpClient clientA = loggedAs(a);
            TestHttpClient clientB = loggedAs(b);

            List<Reply> replies = concurrently(List.of(
                    () -> clientA.send("PUT", "/api/usuarios/" + b.getId(), change("Beta", "USER", true)),
                    () -> clientB.send("PUT", "/api/usuarios/" + a.getId(), change("Alfa", "USER", true))));

            assertThat(replies).as("ronda %d", round).extracting(Reply::status)
                    .allMatch(s -> s == 200 || s == 409)
                    .contains(200);
            // solo uno puede ganar: el otro cambio dejaría la aplicación sin administradores
            assertThat(activeAdmins()).as("administradores activos en la ronda %d", round).isEqualTo(1);
            assertThat(replies.stream().filter(r -> r.status() == 200).count()).as("ronda %d", round).isEqualTo(1L);
        }
    }

    @Test
    void dosAdminsQueSeDesactivanMutuamenteAlMismoTiempoNuncaDejanLaAppSinAdmins() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            users.deleteAll();
            User a = save("Alfa", Role.ADMIN);
            User b = save("Beta", Role.ADMIN);
            TestHttpClient clientA = loggedAs(a);
            TestHttpClient clientB = loggedAs(b);

            List<Reply> replies = concurrently(List.of(
                    () -> clientA.send("PUT", "/api/usuarios/" + b.getId(), change("Beta", "ADMIN", false)),
                    () -> clientB.send("PUT", "/api/usuarios/" + a.getId(), change("Alfa", "ADMIN", false))));

            assertThat(replies).as("ronda %d", round).extracting(Reply::status)
                    .allMatch(s -> s == 200 || s == 409 || s == 401)
                    .contains(200);
            assertThat(activeAdmins()).as("administradores activos en la ronda %d", round).isEqualTo(1);
        }
    }

    // ---- versión de sesión ----

    @Test
    void cambioDeRolYDeContrasenaSimultaneosNoPierdenNingunIncrementoYCierranLaSesion() throws Exception {
        User admin = save("Admin", Role.ADMIN);
        for (int round = 0; round < ROUNDS; round++) {
            User bea = save("Bea" + round, Role.USER);
            TestHttpClient session = loggedAs(bea);
            TestHttpClient clientA = loggedAs(admin);
            TestHttpClient clientB = loggedAs(admin);
            long before = users.findById(bea.getId()).orElseThrow().getSessionVersion();
            String nombre = "Bea " + round;

            List<Reply> replies = concurrently(List.of(
                    () -> clientA.send("PUT", "/api/usuarios/" + bea.getId(), change(nombre, "ADMIN", true)),
                    () -> clientB.send("PUT", "/api/usuarios/" + bea.getId() + "/password",
                            password("otra-clave-" + 123))));

            assertThat(replies).as("ronda %d", round).extracting(Reply::status)
                    .allMatch(s -> s == 200 || s == 204 || s == 409);
            long successes = replies.stream().filter(r -> r.status() != 409).count();
            long after = users.findById(bea.getId()).orElseThrow().getSessionVersion();
            assertThat(after - before).as("incrementos de versión en la ronda %d", round).isEqualTo(successes);
            assertThat(successes).isGreaterThanOrEqualTo(1);
            assertThat(session.get("/api/auth/me").status()).as("sesión de B en la ronda %d", round).isEqualTo(401);
        }
    }

    @Test
    void dosPutSimultaneosSobreElMismoUsuarioDan200Y200o409NuncaUn500() throws Exception {
        User admin = save("Admin", Role.ADMIN);
        User bea = save("Bea", Role.USER);
        for (int round = 0; round < ROUNDS; round++) {
            TestHttpClient clientA = loggedAs(admin);
            TestHttpClient clientB = loggedAs(admin);

            List<Reply> replies = concurrently(List.of(
                    () -> clientA.send("PUT", "/api/usuarios/" + bea.getId(), change("Bea Uno", "ADMIN", true)),
                    () -> clientB.send("PUT", "/api/usuarios/" + bea.getId(), change("Bea Dos", "USER", false))));

            assertThat(replies).as("ronda %d", round).extracting(Reply::status)
                    .allMatch(s -> s == 200 || s == 409)
                    .contains(200);
            replies.stream().filter(r -> r.status() == 409).forEach(r ->
                    assertThat(r.body()).contains("Modificación simultánea"));
        }
    }
}
