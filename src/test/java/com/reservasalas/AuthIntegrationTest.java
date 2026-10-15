package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.Room;
import com.reservasalas.entity.User;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.RoomRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.support.TestHttpClient;
import com.reservasalas.support.TestHttpClient.Reply;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Autenticación, sesión, CSRF y permisos con la cadena de seguridad real, sobre un servidor HTTP
 * levantado en un puerto aleatorio (así se ven las cookies reales del contenedor).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:auth-${random.uuid}")
@ExtendWith(OutputCaptureExtension.class)
class AuthIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba-1";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoomRepository rooms;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private PasswordEncoder encoder;

    private User ana;
    private User bea;
    private User admin;
    private Room room;

    @BeforeEach
    void setUp() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        ana = users.save(new User("ana-" + sufijo + "@example.com", "Ana", encoder.encode(PASSWORD), Role.USER, Instant.EPOCH));
        bea = users.save(new User("bea-" + sufijo + "@example.com", "Bea", encoder.encode(PASSWORD), Role.USER, Instant.EPOCH));
        admin = users.save(new User("admin-" + sufijo + "@example.com", "Admin", encoder.encode(PASSWORD),
                Role.ADMIN, Instant.EPOCH));
        room = rooms.save(new Room("Sala " + sufijo, 10, 1));
    }

    private TestHttpClient anonymous() {
        TestHttpClient client = new TestHttpClient(port);
        assertThat(client.get("/api/auth/csrf").status()).isEqualTo(204);
        return client;
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private TestHttpClient loggedAs(User user) {
        TestHttpClient client = anonymous();
        Reply reply = client.send("POST", "/api/auth/login", credentials(user.getEmail(), PASSWORD));
        assertThat(reply.status()).isEqualTo(200);
        return client;
    }

    private Reservation reservationOf(User owner) {
        return reservations.save(new Reservation(room, LocalDate.now().plusYears(5), LocalTime.of(10, 0),
                LocalTime.of(11, 0), owner, "Reunión de prueba " + UUID.randomUUID()));
    }

    private static JsonNode json(Reply reply) throws Exception {
        return JSON.readTree(reply.body());
    }

    // ---- csrf ----

    @Test
    void csrfDevuelve204ConCookieLegibleDesdeJavaScriptYSinSesion() {
        Reply reply = new TestHttpClient(port).get("/api/auth/csrf");

        assertThat(reply.status()).isEqualTo(204);
        String cookie = reply.setCookie("XSRF-TOKEN").orElseThrow();
        assertThat(cookie).doesNotContainIgnoringCase("HttpOnly");
        assertThat(cookie).containsIgnoringCase("SameSite=Strict");
        assertThat(reply.setCookie("JSESSIONID")).isEmpty();
    }

    // ---- login ----

    @Test
    void loginDevuelve200ConUsuarioYCookieDeSesionHttpOnlySameSiteStrict() throws Exception {
        TestHttpClient client = anonymous();

        Reply reply = client.send("POST", "/api/auth/login", credentials(ana.getEmail(), PASSWORD));

        assertThat(reply.status()).isEqualTo(200);
        JsonNode body = json(reply);
        assertThat(body.get("id").asLong()).isEqualTo(ana.getId());
        assertThat(body.get("email").asText()).isEqualTo(ana.getEmail());
        assertThat(body.get("nombre").asText()).isEqualTo("Ana");
        assertThat(body.get("rol").asText()).isEqualTo("USER");
        assertThat(body.has("password")).isFalse();
        assertThat(body.has("passwordHash")).isFalse();
        assertThat(reply.body()).doesNotContain("$2");
        String session = reply.setCookie("JSESSIONID").orElseThrow();
        assertThat(session).containsIgnoringCase("HttpOnly");
        assertThat(session).containsIgnoringCase("SameSite=Strict");
    }

    @Test
    void loginCambiaElIdentificadorDeSesionYEmiteUnTokenCsrfNuevo() {
        TestHttpClient client = loggedAs(ana);
        String oldSession = client.cookie("JSESSIONID");
        String oldCsrf = client.cookie("XSRF-TOKEN");

        Reply second = client.send("POST", "/api/auth/login", credentials(ana.getEmail(), PASSWORD));

        assertThat(second.status()).isEqualTo(200);
        assertThat(client.cookie("JSESSIONID")).isNotEqualTo(oldSession);
        assertThat(client.cookie("XSRF-TOKEN")).isNotEqualTo(oldCsrf);
        TestHttpClient attacker = new TestHttpClient(port);
        attacker.setCookie("JSESSIONID", oldSession);
        assertThat(attacker.get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void loginFallidoConEmailInexistenteOContrasenaIncorrectaDa401ConElMismoCuerpo() throws Exception {
        TestHttpClient client = anonymous();

        Reply noExiste = client.send("POST", "/api/auth/login", credentials("nadie@example.com", PASSWORD));
        Reply mala = client.send("POST", "/api/auth/login", credentials(ana.getEmail(), "otra-clave"));

        assertThat(noExiste.status()).isEqualTo(401);
        assertThat(mala.status()).isEqualTo(401);
        assertThat(noExiste.body()).isEqualTo(mala.body());
        assertThat(json(noExiste).get("status").asInt()).isEqualTo(401);
        assertThat(noExiste.setCookie("JSESSIONID")).isEmpty();
        assertThat(mala.setCookie("JSESSIONID")).isEmpty();
        assertThat(client.get("/api/auth/me").status()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"email\":\"ana@example.com\"}",
            "{\"password\":\"x\"}",
            "{\"email\":\"\",\"password\":\"x\"}",
            "{\"email\":\"no-es-un-email\",\"password\":\"x\"}",
            "{\"email\":\"ana@example.com\",\"password\":\"\"}",
            "{\"email\":null,\"password\":null}",
            "esto no es json"})
    void loginConCuerpoInvalidoDa400(String body) {
        TestHttpClient client = anonymous();

        Reply reply = client.send("POST", "/api/auth/login", body);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.setCookie("JSESSIONID")).isEmpty();
    }

    @Test
    void loginConContrasenaDeMasDe128CaracteresDa400() {
        TestHttpClient client = anonymous();

        Reply reply = client.send("POST", "/api/auth/login", credentials(ana.getEmail(), "a".repeat(129)));

        assertThat(reply.status()).isEqualTo(400);
    }

    @Test
    void loginConContrasenaDeMasDe72BytesDa401ConElMismoCuerpoQueUnaIncorrecta() {
        TestHttpClient client = anonymous();

        Reply larga = client.send("POST", "/api/auth/login", credentials(ana.getEmail(), "p".repeat(100)));
        Reply mala = client.send("POST", "/api/auth/login", credentials(ana.getEmail(), "otra-clave"));

        assertThat(larga.status()).isEqualTo(401);
        assertThat(larga.body()).isEqualTo(mala.body());
        assertThat(larga.setCookie("JSESSIONID")).isEmpty();
    }

    @Test
    void loginSinCabeceraCsrfDa403() {
        TestHttpClient client = anonymous();

        Reply sinCabecera = client.send("POST", "/api/auth/login", credentials(ana.getEmail(), PASSWORD), false);
        Reply tokenFalso = client.send("POST", "/api/auth/login", credentials(ana.getEmail(), PASSWORD),
                "token-falso");

        assertThat(sinCabecera.status()).isEqualTo(403);
        assertThat(tokenFalso.status()).isEqualTo(403);
        assertThat(sinCabecera.setCookie("JSESSIONID")).isEmpty();
        assertThat(client.get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void loginAceptaElEmailEnMayusculas() throws Exception {
        TestHttpClient client = anonymous();

        Reply reply = client.send("POST", "/api/auth/login",
                credentials(ana.getEmail().toUpperCase(), PASSWORD));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(json(reply).get("email").asText()).isEqualTo(ana.getEmail());
    }

    // ---- me y logout ----

    @Test
    void meDevuelve200ConElUsuarioDeLaSesion() throws Exception {
        TestHttpClient client = loggedAs(admin);

        Reply reply = client.get("/api/auth/me");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(json(reply).get("id").asLong()).isEqualTo(admin.getId());
        assertThat(json(reply).get("rol").asText()).isEqualTo("ADMIN");
        assertThat(json(reply).get("nombre").asText()).isEqualTo("Admin");
    }

    @Test
    void meSinSesionDa401() {
        assertThat(new TestHttpClient(port).get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void logoutDa204YDespuesMeDa401() {
        TestHttpClient client = loggedAs(ana);
        String oldSession = client.cookie("JSESSIONID");

        Reply logout = client.send("POST", "/api/auth/logout", null);

        assertThat(logout.status()).isEqualTo(204);
        assertThat(client.get("/api/auth/me").status()).isEqualTo(401);
        TestHttpClient replay = new TestHttpClient(port);
        replay.setCookie("JSESSIONID", oldSession);
        assertThat(replay.get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void logoutSinCabeceraCsrfDa403YNoCierraLaSesion() {
        TestHttpClient client = loggedAs(ana);

        assertThat(client.send("POST", "/api/auth/logout", null, false).status()).isEqualTo(403);
        assertThat(client.get("/api/auth/me").status()).isEqualTo(200);
    }

    // ---- sin sesión ----

    static Stream<Arguments> endpointsDeLaApi() {
        String reserva = "{\"salaId\":1,\"fecha\":\"2099-01-01\",\"horaInicio\":\"10:00\",\"horaFin\":\"11:00\","
                + "\"motivo\":\"x\"}";
        return Stream.of(
                Arguments.of("GET", "/api/reservas", null),
                Arguments.of("GET", "/api/reservas/1", null),
                Arguments.of("POST", "/api/reservas", reserva),
                Arguments.of("DELETE", "/api/reservas/1", null),
                Arguments.of("GET", "/api/salas", null),
                Arguments.of("POST", "/api/salas", "{\"nombre\":\"x\",\"capacidad\":1,\"planta\":1}"),
                Arguments.of("PUT", "/api/salas/1", "{\"nombre\":\"x\",\"capacidad\":1,\"planta\":1}"),
                Arguments.of("DELETE", "/api/salas/1", null),
                Arguments.of("GET", "/api/auth/me", null),
                Arguments.of("POST", "/api/auth/logout", null));
    }

    @ParameterizedTest(name = "{0} {1} sin sesión da 401")
    @MethodSource("endpointsDeLaApi")
    void sinSesionTodoLaApiDa401ComoProblemDetailSinWwwAuthenticate(String method, String path, String body)
            throws Exception {
        TestHttpClient client = anonymous();

        Reply reply = client.send(method, path, body);

        assertThat(reply.status()).isEqualTo(401);
        assertThat(reply.header("WWW-Authenticate")).isEmpty();
        assertThat(reply.header("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(json(reply).get("status").asInt()).isEqualTo(401);
        assertThat(reply.setCookie("JSESSIONID")).isEmpty();
    }

    @Test
    void unaSesionInvalidadaDa401EnLaSiguientePeticion() {
        TestHttpClient client = loggedAs(ana);
        assertThat(client.get("/api/reservas").status()).isEqualTo(200);
        String session = client.cookie("JSESSIONID");
        loggedOutElsewhere(session);

        assertThat(client.get("/api/reservas").status()).isEqualTo(401);
        assertThat(client.get("/api/auth/me").status()).isEqualTo(401);
    }

    /** Destruye la sesión en el servidor, como haría su caducidad. */
    private void loggedOutElsewhere(String session) {
        TestHttpClient other = new TestHttpClient(port);
        other.setCookie("JSESSIONID", session);
        assertThat(other.get("/api/auth/csrf").status()).isEqualTo(204);
        assertThat(other.send("POST", "/api/auth/logout", null).status()).isEqualTo(204);
    }

    // ---- permisos ----

    @ParameterizedTest(name = "USER: {0} {1} da 403")
    @MethodSource("salasDeEscritura")
    void unUsuarioNormalNoPuedeModificarSalas(String method, String path, String body) {
        TestHttpClient client = loggedAs(ana);

        assertThat(client.send(method, path, body).status()).isEqualTo(403);
    }

    @ParameterizedTest(name = "ADMIN: {0} {1} no da 401 ni 403")
    @MethodSource("salasDeEscritura")
    void unAdministradorSuperaLaAutorizacionDeSalas(String method, String path, String body) {
        TestHttpClient client = loggedAs(admin);

        assertThat(client.send(method, path, body).status()).isNotIn(401, 403);
    }

    static Stream<Arguments> salasDeEscritura() {
        String sala = "{\"nombre\":\"x\",\"capacidad\":1,\"planta\":1}";
        return Stream.of(
                Arguments.of("POST", "/api/salas", sala),
                Arguments.of("PUT", "/api/salas/1", sala),
                Arguments.of("DELETE", "/api/salas/1", null));
    }

    @Test
    void unUsuarioNormalPuedeListarSalas() {
        assertThat(loggedAs(ana).get("/api/salas").status()).isEqualTo(200);
    }

    @Test
    void unUsuarioCancelaSuReservaPeroNoLaAjena() {
        Reservation propia = reservationOf(ana);
        Reservation ajena = reservationOf(bea);
        TestHttpClient client = loggedAs(ana);

        assertThat(client.send("DELETE", "/api/reservas/" + ajena.getId(), null).status()).isEqualTo(403);
        assertThat(reservations.existsById(ajena.getId())).isTrue();
        assertThat(client.send("DELETE", "/api/reservas/" + propia.getId(), null).status()).isEqualTo(204);
        assertThat(reservations.existsById(propia.getId())).isFalse();
    }

    @Test
    void unAdministradorCancelaUnaReservaAjena() {
        Reservation ajena = reservationOf(bea);

        assertThat(loggedAs(admin).send("DELETE", "/api/reservas/" + ajena.getId(), null).status())
                .isEqualTo(204);
        assertThat(reservations.existsById(ajena.getId())).isFalse();
    }

    @Test
    void cancelarUnaReservaInexistenteDa404() {
        assertThat(loggedAs(ana).send("DELETE", "/api/reservas/999999999", null).status()).isEqualTo(404);
    }

    // ---- crear reserva ----

    @Test
    void crearReservaTomaElUsuarioDeLaSesionEIgnoraElResponsableDelCuerpo() throws Exception {
        TestHttpClient client = loggedAs(ana);
        String body = """
                {"salaId":%d,"fecha":"%s","horaInicio":"09:00","horaFin":"10:00",
                 "motivo":"Reunión","responsable":"Intruso","usuarioId":%d}"""
                .formatted(room.getId(), LocalDate.now().plusYears(5), bea.getId());

        Reply reply = client.send("POST", "/api/reservas", body);

        assertThat(reply.status()).isEqualTo(201);
        JsonNode created = json(reply);
        assertThat(created.get("usuarioId").asLong()).isEqualTo(ana.getId());
        assertThat(created.get("responsable").asText()).isEqualTo("Ana");
        JsonNode stored = json(client.get("/api/reservas/" + created.get("id").asLong()));
        assertThat(stored.get("usuarioId").asLong()).isEqualTo(ana.getId());
        assertThat(stored.get("responsable").asText()).isEqualTo("Ana");
    }

    @Test
    void crearReservaSinResponsableEsValido() {
        TestHttpClient client = loggedAs(bea);
        String body = """
                {"salaId":%d,"fecha":"%s","horaInicio":"12:00","horaFin":"13:00","motivo":"Sin responsable"}"""
                .formatted(room.getId(), LocalDate.now().plusYears(5));

        assertThat(client.send("POST", "/api/reservas", body).status()).isEqualTo(201);
    }

    // ---- contraseñas ----

    @Test
    void lasContrasenasSeGuardanConBcryptYNuncaEnClaro() {
        User stored = users.findByEmail(ana.getEmail()).orElseThrow();

        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(stored.getPasswordHash()).matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(users.findAll()).allSatisfy(u -> assertThat(u.getPasswordHash()).doesNotContain(PASSWORD));
    }

    // ---- logs ----

    @Test
    void ningunLogContieneLaContrasenaDelLogin(CapturedOutput output) {
        String secreta = "secreta-que-no-debe-salir-9";
        TestHttpClient client = anonymous();

        client.send("POST", "/api/auth/login", credentials(ana.getEmail(), secreta));
        client.send("POST", "/api/auth/login", credentials("nadie@example.com", secreta));
        client.send("POST", "/api/auth/login", "{\"email\":\"no-email\",\"password\":\"" + secreta + "\"}");
        client.send("POST", "/api/auth/login", "{\"password\":\"" + secreta + "\" roto");
        client.send("POST", "/api/auth/login", credentials(ana.getEmail(), secreta), false);
        client.send("POST", "/api/auth/login", credentials(ana.getEmail(), PASSWORD));

        assertThat(output.getAll()).doesNotContain(secreta).doesNotContain(PASSWORD);
    }
}
