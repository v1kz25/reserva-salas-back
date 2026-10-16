package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;

import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.support.TestHttpClient;
import com.reservasalas.support.TestHttpClient.Reply;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gestión de usuarios por un administrador con la cadena de seguridad real: permisos, CRUD,
 * validaciones, contraseñas y cierre de sesiones al cambiar el rol, el estado o la contraseña.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:usuarios-${random.uuid}")
@ExtendWith(OutputCaptureExtension.class)
class GestionUsuariosIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba-1";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder encoder;

    private String suffix;
    private User admin;
    private User ana;
    private User bea;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = save("admin-" + suffix + "@example.com", "Admin", Role.ADMIN);
        ana = save("ana-" + suffix + "@example.com", "Ana", Role.USER);
        bea = save("bea-" + suffix + "@example.com", "Bea", Role.USER);
    }

    private User save(String email, String name, Role role) {
        return users.save(new User(email, name, encoder.encode(PASSWORD), role, Instant.EPOCH));
    }

    private TestHttpClient loggedAs(User user) {
        return loggedAs(user.getEmail(), PASSWORD);
    }

    private TestHttpClient loggedAs(String email, String password) {
        TestHttpClient client = new TestHttpClient(port);
        assertThat(client.get("/api/auth/csrf").status()).isEqualTo(204);
        Reply reply = client.send("POST", "/api/auth/login", credentials(email, password));
        assertThat(reply.status()).isEqualTo(200);
        return client;
    }

    private Reply login(String email, String password) {
        TestHttpClient client = new TestHttpClient(port);
        client.get("/api/auth/csrf");
        return client.send("POST", "/api/auth/login", credentials(email, password));
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private static String newUser(String email, String name, String password, String role) {
        return "{\"email\":\"%s\",\"nombre\":\"%s\",\"password\":\"%s\"%s}".formatted(email, name, password,
                role == null ? "" : ",\"rol\":\"" + role + "\"");
    }

    private static String change(String name, String role, boolean active) {
        return "{\"nombre\":\"%s\",\"rol\":\"%s\",\"activo\":%s}".formatted(name, role, active);
    }

    private static String password(String value) {
        return "{\"password\":\"%s\"}".formatted(value);
    }

    private static JsonNode json(Reply reply) throws Exception {
        return JSON.readTree(reply.body());
    }

    // ---- permisos ----

    private List<Reply> allEndpoints(TestHttpClient client) {
        String id = String.valueOf(bea.getId());
        List<Reply> replies = new ArrayList<>();
        replies.add(client.get("/api/usuarios"));
        replies.add(client.get("/api/usuarios/" + id));
        replies.add(client.send("POST", "/api/usuarios", newUser("x-" + suffix + "@example.com", "X", PASSWORD, null)));
        replies.add(client.send("PUT", "/api/usuarios/" + id, change("Bea", "USER", true)));
        replies.add(client.send("PUT", "/api/usuarios/" + id + "/password", password(PASSWORD)));
        return replies;
    }

    @Test
    void sinSesionTodosLosEndpointsDan401() {
        TestHttpClient client = new TestHttpClient(port);
        client.get("/api/auth/csrf");

        assertThat(allEndpoints(client)).extracting(Reply::status).containsOnly(401);
    }

    @Test
    void unUsuarioNormalRecibe403EnTodosLosEndpoints() {
        assertThat(allEndpoints(loggedAs(ana))).extracting(Reply::status).containsOnly(403);
        assertThat(users.findById(bea.getId()).orElseThrow().isActive()).isTrue();
        assertThat(users.existsByEmail("x-" + suffix + "@example.com")).isFalse();
    }

    // PROVISIONAL: GET /api/usuarios/buscar aun no existe (hoy cae en /{id} y da 400); solo se
    // comprueba que la regla de seguridad lo deja pasar a un usuario autenticado. Lo sustituye
    // la #6 con los tests del buscador real.
    @Test
    void buscarNoDa403AUnUsuarioNormal() {
        assertThat(loggedAs(ana).get("/api/usuarios/buscar").status()).isNotEqualTo(403).isNotEqualTo(401);
    }

    @Test
    void buscarSinSesionDa401() {
        TestHttpClient client = new TestHttpClient(port);
        assertThat(client.get("/api/usuarios/buscar").status()).isEqualTo(401);
    }

    // ---- consulta ----

    @Test
    void listadoOrdenadoPorNombreConTodosLosCampos() throws Exception {
        save("zeta-" + suffix + "@example.com", "Zzz" + suffix, Role.USER);
        save("alfa-" + suffix + "@example.com", "AAA" + suffix, Role.USER);

        Reply reply = loggedAs(admin).get("/api/usuarios");

        assertThat(reply.status()).isEqualTo(200);
        List<String> names = new ArrayList<>();
        json(reply).forEach(n -> names.add(n.get("nombre").asText()));
        assertThat(names).isSortedAccordingTo(String::compareTo);
        assertThat(names.indexOf("AAA" + suffix)).isLessThan(names.indexOf("Ana"));
        assertThat(names.indexOf("Ana")).isLessThan(names.indexOf("Zzz" + suffix));
        JsonNode first = json(reply).get(0);
        assertThat(first.has("id") && first.has("email") && first.has("rol") && first.has("activo")
                && first.has("fechaCreacion")).isTrue();
        assertThat(reply.body()).doesNotContain("password").doesNotContain("$2");
    }

    @Test
    void getPorIdDevuelveElUsuario() throws Exception {
        Reply reply = loggedAs(admin).get("/api/usuarios/" + ana.getId());

        assertThat(reply.status()).isEqualTo(200);
        assertThat(json(reply).get("id").asLong()).isEqualTo(ana.getId());
        assertThat(json(reply).get("email").asText()).isEqualTo(ana.getEmail());
        assertThat(json(reply).get("rol").asText()).isEqualTo("USER");
        assertThat(json(reply).get("activo").asBoolean()).isTrue();
    }

    @Test
    void getDeUnIdInexistenteDa404() {
        assertThat(loggedAs(admin).get("/api/usuarios/999999999").status()).isEqualTo(404);
    }

    @Test
    void putYPasswordDeUnIdInexistenteDan404() {
        TestHttpClient client = loggedAs(admin);
        assertThat(client.send("PUT", "/api/usuarios/999999999", change("X", "USER", true)).status()).isEqualTo(404);
        assertThat(client.send("PUT", "/api/usuarios/999999999/password", password(PASSWORD)).status())
                .isEqualTo(404);
    }

    // ---- alta ----

    @Test
    void altaDevuelve201ConLocationRolUserPorDefectoYFechaDeCreacion() throws Exception {
        String email = "nuevo-" + suffix + "@example.com";

        Reply reply = loggedAs(admin).send("POST", "/api/usuarios", newUser(email, "Nuevo", PASSWORD, null));

        assertThat(reply.status()).isEqualTo(201);
        JsonNode body = json(reply);
        assertThat(reply.header("Location").orElseThrow()).endsWith("/api/usuarios/" + body.get("id").asLong());
        assertThat(body.get("rol").asText()).isEqualTo("USER");
        assertThat(body.get("activo").asBoolean()).isTrue();
        assertThat(body.get("fechaCreacion").asText()).isNotBlank();
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(reply.body()).doesNotContain(PASSWORD);
    }

    @Test
    void altaConRolAdminCreaUnAdministrador() throws Exception {
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios",
                newUser("adm2-" + suffix + "@example.com", "Otro", PASSWORD, "ADMIN"));

        assertThat(reply.status()).isEqualTo(201);
        assertThat(json(reply).get("rol").asText()).isEqualTo("ADMIN");
    }

    @Test
    void altaNormalizaElEmailYGuardaLaContrasenaEnBcrypt() throws Exception {
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios",
                newUser("  Mixto-" + suffix + "@Example.COM ", "Mixto", PASSWORD, null));

        assertThat(reply.status()).isEqualTo(201);
        String email = "mixto-" + suffix + "@example.com";
        assertThat(json(reply).get("email").asText()).isEqualTo(email);
        User stored = users.findById(json(reply).get("id").asLong()).orElseThrow();
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(login(email, PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void altaConEmailDuplicadoConOtrasMayusculasDa409() {
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios",
                newUser(ana.getEmail().toUpperCase(), "Otra Ana", PASSWORD, null));

        assertThat(reply.status()).isEqualTo(409);
        assertThat(users.findAll().stream().filter(u -> u.getEmail().equalsIgnoreCase(ana.getEmail())).count())
                .isEqualTo(1);
    }

    @Test
    void altaConEmailDuplicadoNoExponeElEmailEnLaRespuesta() {
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios", newUser(ana.getEmail(), "Otra", PASSWORD, null));

        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.body()).doesNotContain(ana.getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no-es-un-email", "sin-arroba.example.com", "a@"})
    void altaConEmailVacioOInvalidoDa400(String email) {
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios", newUser(email, "Nombre", PASSWORD, null));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body()).contains("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void altaConNombreVacioDa400(String name) {
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios",
                newUser("v-" + suffix + "@example.com", name, PASSWORD, null));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body()).contains("nombre");
    }

    @Test
    void altaConContrasenaDeSieteCaracteresDa400YDeOchoEsValida() {
        TestHttpClient client = loggedAs(admin);

        assertThat(client.send("POST", "/api/usuarios",
                newUser("p7-" + suffix + "@example.com", "P", "1234567", null)).status()).isEqualTo(400);
        assertThat(client.send("POST", "/api/usuarios",
                newUser("p8-" + suffix + "@example.com", "P", "12345678", null)).status()).isEqualTo(201);
    }

    @Test
    void altaConContrasenaDe73BytesMultibyteDa400() {
        String password = "é".repeat(37); // 37 caracteres, 74 bytes
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios",
                newUser("mb-" + suffix + "@example.com", "M", password, null));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(users.existsByEmail("mb-" + suffix + "@example.com")).isFalse();
    }

    @Test
    void altaConContrasenaDe72BytesExactosEsValida() {
        String password = "é".repeat(36); // 72 bytes
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios",
                newUser("mb72-" + suffix + "@example.com", "M", password, null));

        assertThat(reply.status()).isEqualTo(201);
    }

    @Test
    void altaConContrasenaDeMasDe72CaracteresDa400() {
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios",
                newUser("l-" + suffix + "@example.com", "L", "a".repeat(73), null));

        assertThat(reply.status()).isEqualTo(400);
    }

    @Test
    void altaConRolNoValidoDa400() {
        Reply reply = loggedAs(admin).send("POST", "/api/usuarios",
                newUser("r-" + suffix + "@example.com", "R", PASSWORD, "SUPERADMIN"));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(users.existsByEmail("r-" + suffix + "@example.com")).isFalse();
    }

    @Test
    void altaSinCsrfDa403() {
        Reply reply = loggedAsAdminWithoutCsrf();

        assertThat(reply.status()).isEqualTo(403);
    }

    private Reply loggedAsAdminWithoutCsrf() {
        TestHttpClient client = loggedAs(admin);
        return client.send("POST", "/api/usuarios",
                newUser("csrf-" + suffix + "@example.com", "C", PASSWORD, null), false);
    }

    // ---- modificación ----

    @Test
    void putCambiaNombreRolYActivo() throws Exception {
        Reply reply = loggedAs(admin).send("PUT", "/api/usuarios/" + ana.getId(), change("Ana Nueva", "ADMIN", false));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(json(reply).get("nombre").asText()).isEqualTo("Ana Nueva");
        assertThat(json(reply).get("rol").asText()).isEqualTo("ADMIN");
        assertThat(json(reply).get("activo").asBoolean()).isFalse();
        User stored = users.findById(ana.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Ana Nueva");
        assertThat(stored.getRole()).isEqualTo(Role.ADMIN);
        assertThat(stored.isActive()).isFalse();
    }

    @Test
    void putConDatosNoValidosDa400() {
        TestHttpClient client = loggedAs(admin);
        String url = "/api/usuarios/" + ana.getId();

        assertThat(client.send("PUT", url, change("", "USER", true)).status()).isEqualTo(400);
        assertThat(client.send("PUT", url, change("Ana", "JEFE", true)).status()).isEqualTo(400);
        assertThat(client.send("PUT", url, "{\"nombre\":\"Ana\",\"rol\":\"USER\"}").status()).isEqualTo(400);
        assertThat(users.findById(ana.getId()).orElseThrow().getName()).isEqualTo("Ana");
    }

    @Test
    void elAdminNoPuedeDesactivarseASiMismo() {
        Reply reply = loggedAs(admin).send("PUT", "/api/usuarios/" + admin.getId(),
                change("Admin", "ADMIN", false));

        assertThat(reply.status()).isEqualTo(409);
        assertThat(users.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void elAdminNoPuedeQuitarseElRolAdmin() {
        TestHttpClient client = loggedAs(admin);
        Reply reply = client.send("PUT", "/api/usuarios/" + admin.getId(), change("Admin", "USER", true));

        assertThat(reply.status()).isEqualTo(409);
        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(client.get("/api/usuarios").status()).isEqualTo(200);
    }

    @Test
    void elAdminPuedeCambiarSuPropioNombreYConservaLaSesion() throws Exception {
        TestHttpClient client = loggedAs(admin);

        Reply reply = client.send("PUT", "/api/usuarios/" + admin.getId(), change("Nuevo Nombre", "ADMIN", true));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(json(reply).get("nombre").asText()).isEqualTo("Nuevo Nombre");
        assertThat(client.get("/api/usuarios").status()).isEqualTo(200);
        assertThat(json(client.get("/api/auth/me")).get("nombre").asText()).isEqualTo("Nuevo Nombre");
    }

    @Test
    void unAdminPuedeQuitarElRolOdesactivarAOtroAdmin() {
        User otro = save("otro-" + suffix + "@example.com", "Otro", Role.ADMIN);
        TestHttpClient client = loggedAs(admin);

        assertThat(client.send("PUT", "/api/usuarios/" + otro.getId(), change("Otro", "USER", false)).status())
                .isEqualTo(200);
    }

    // ---- sesiones ----

    @Test
    void desactivarAUnUsuarioCierraSuSesionAbierta() {
        TestHttpClient session = loggedAs(bea);
        assertThat(session.get("/api/auth/me").status()).isEqualTo(200);

        assertThat(loggedAs(admin).send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", false)).status())
                .isEqualTo(200);

        assertThat(session.get("/api/auth/me").status()).isEqualTo(401);
        assertThat(session.get("/api/reservas").status()).isEqualTo(401);
    }

    @Test
    void cambiarElRolDeUnUsuarioCierraSuSesionYAlVolverAEntrarTieneElNuevoRol() throws Exception {
        TestHttpClient session = loggedAs(bea);

        assertThat(loggedAs(admin).send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "ADMIN", true)).status())
                .isEqualTo(200);

        assertThat(session.get("/api/auth/me").status()).isEqualTo(401);
        TestHttpClient again = loggedAs(bea);
        assertThat(json(again.get("/api/auth/me")).get("rol").asText()).isEqualTo("ADMIN");
        assertThat(again.get("/api/usuarios").status()).isEqualTo(200);
    }

    @Test
    void cambiarLaContrasenaDeUnUsuarioCierraSuSesion() {
        TestHttpClient session = loggedAs(bea);

        assertThat(loggedAs(admin).send("PUT", "/api/usuarios/" + bea.getId() + "/password",
                password("otra-clave-123")).status()).isEqualTo(204);

        assertThat(session.get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void cambiarSoloElNombreNoCierraLaSesion() {
        TestHttpClient session = loggedAs(bea);

        assertThat(loggedAs(admin).send("PUT", "/api/usuarios/" + bea.getId(), change("Bea Renombrada", "USER", true))
                .status()).isEqualTo(200);

        assertThat(session.get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void guardarSinCambiosRealesNoCierraLaSesion() {
        TestHttpClient session = loggedAs(bea);

        assertThat(loggedAs(admin).send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", true)).status())
                .isEqualTo(200);

        assertThat(session.get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void unUsuarioDesactivadoNoPuedeHacerLoginYRecibeElMismoCuerpoQueConCredencialesMalas() {
        loggedAs(admin).send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", false));

        Reply inactive = login(bea.getEmail(), PASSWORD);
        Reply wrong = login(ana.getEmail(), "clave-incorrecta");
        Reply unknown = login("nadie-" + suffix + "@example.com", PASSWORD);

        assertThat(inactive.status()).isEqualTo(401);
        assertThat(inactive.body()).isEqualTo(wrong.body()).isEqualTo(unknown.body());
        assertThat(inactive.setCookie("JSESSIONID")).isEmpty();
    }

    @Test
    void unUsuarioDesactivadoConContrasenaMalaTambienDa401ConElMismoCuerpo() {
        loggedAs(admin).send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", false));

        assertThat(login(bea.getEmail(), "mala").body()).isEqualTo(login(bea.getEmail(), PASSWORD).body());
    }

    @Test
    void alReactivarloPuedeVolverAEntrar() {
        TestHttpClient client = loggedAs(admin);
        client.send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", false));
        assertThat(login(bea.getEmail(), PASSWORD).status()).isEqualTo(401);

        assertThat(client.send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", true)).status())
                .isEqualTo(200);

        assertThat(login(bea.getEmail(), PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void unUsuarioDesactivadoQuedaEnElListadoComoInactivo() throws Exception {
        TestHttpClient client = loggedAs(admin);
        client.send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", false));

        assertThat(json(client.get("/api/usuarios/" + bea.getId())).get("activo").asBoolean()).isFalse();
    }

    @Test
    void elAdminQueCambiaSuPropiaContrasenaConservaLaSesionActualPeroSeCierranLasDeOtrosClientes() {
        TestHttpClient current = loggedAs(admin);
        TestHttpClient other = loggedAs(admin);

        Reply reply = current.send("PUT", "/api/usuarios/" + admin.getId() + "/password", password("nueva-clave-99"));

        assertThat(reply.status()).isEqualTo(204);
        assertThat(current.get("/api/usuarios").status()).isEqualTo(200);
        assertThat(current.send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", true)).status())
                .isEqualTo(200);
        assertThat(other.get("/api/auth/me").status()).isEqualTo(401);
        assertThat(login(admin.getEmail(), PASSWORD).status()).isEqualTo(401);
        assertThat(login(admin.getEmail(), "nueva-clave-99").status()).isEqualTo(200);
    }

    @Test
    void elAdminQueCambiaSuPropioNombreConservaLaSesionYSeCierranLasDeOtrosClientesSoloSiCambiaAlgoDeSeguridad() {
        TestHttpClient current = loggedAs(admin);
        TestHttpClient other = loggedAs(admin);

        current.send("PUT", "/api/usuarios/" + admin.getId(), change("Otro Nombre", "ADMIN", true));

        assertThat(current.get("/api/auth/me").status()).isEqualTo(200);
        assertThat(other.get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void conUnaSesionInvalidadaUnPostOUnPutDan401No403() {
        TestHttpClient session = loggedAs(bea);
        assertThat(loggedAs(admin).send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "ADMIN", true)).status())
                .isEqualTo(200);

        Reply post = session.send("POST", "/api/usuarios", newUser("inv-" + suffix + "@example.com", "I", PASSWORD, null));
        TestHttpClient session2 = loggedAs(ana);
        loggedAs(admin).send("PUT", "/api/usuarios/" + ana.getId() + "/password", password("otra-clave-123"));
        Reply put = session2.send("PUT", "/api/usuarios/" + ana.getId(), change("Ana", "USER", true));

        assertThat(post.status()).isEqualTo(401);
        assertThat(put.status()).isEqualTo(401);
        assertThat(users.existsByEmail("inv-" + suffix + "@example.com")).isFalse();
    }

    // ---- contraseña ----

    @Test
    void cambiarLaContrasenaDa204YLaNuevaFuncionaYLaAntiguaNo() {
        Reply reply = loggedAs(admin).send("PUT", "/api/usuarios/" + ana.getId() + "/password",
                password("nueva-clave-99"));

        assertThat(reply.status()).isEqualTo(204);
        assertThat(reply.body()).isEmpty();
        assertThat(login(ana.getEmail(), "nueva-clave-99").status()).isEqualTo(200);
        assertThat(login(ana.getEmail(), PASSWORD).status()).isEqualTo(401);
        assertThat(users.findById(ana.getId()).orElseThrow().getPasswordHash()).startsWith("$2");
    }

    @Test
    void cambiarLaContrasenaValidaLongitudYBytes() {
        TestHttpClient client = loggedAs(admin);
        String url = "/api/usuarios/" + ana.getId() + "/password";

        assertThat(client.send("PUT", url, password("1234567")).status()).isEqualTo(400);
        assertThat(client.send("PUT", url, password("é".repeat(37))).status()).isEqualTo(400);
        assertThat(client.send("PUT", url, password("a".repeat(73))).status()).isEqualTo(400);
        assertThat(client.send("PUT", url, "{}").status()).isEqualTo(400);
        assertThat(login(ana.getEmail(), PASSWORD).status()).isEqualTo(200);
    }

    // ---- log ----

    @Test
    void elLogNoContieneEmailsNiContrasenas(CapturedOutput output) {
        String email = "secreto-" + suffix + "@example.com";
        String secret = "clave-secreta-" + suffix;
        TestHttpClient client = loggedAs(admin);
        TestHttpClient invalidated = loggedAs(bea);
        client.send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "ADMIN", true));
        invalidated.get("/api/usuarios");
        invalidated.send("POST", "/api/usuarios", newUser(email, "Secreto", secret, null));

        client.send("POST", "/api/usuarios", newUser(email, "Secreto", secret, null));
        client.send("POST", "/api/usuarios", newUser(email.toUpperCase(), "Secreto", secret, null));
        client.send("POST", "/api/usuarios", newUser("mal", "Secreto", secret, null));
        client.send("POST", "/api/usuarios", newUser("tl-" + suffix + "@example.com", "T", "é".repeat(40), null));
        client.send("PUT", "/api/usuarios/" + ana.getId() + "/password", password("nueva-" + secret));
        client.send("PUT", "/api/usuarios/" + bea.getId(), change("Bea", "USER", false));
        client.send("PUT", "/api/usuarios/" + admin.getId(), change("Admin", "USER", true));
        login(bea.getEmail(), PASSWORD);
        login(ana.getEmail(), "otra-" + secret);

        assertThat(output.getAll())
                .doesNotContainIgnoringCase(email)
                .doesNotContain(secret)
                .doesNotContainIgnoringCase("@example.com")
                .doesNotContain(PASSWORD);
    }
}
