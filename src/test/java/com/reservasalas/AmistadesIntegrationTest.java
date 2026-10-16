package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.repository.FriendRequestRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.support.TestUsers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Amistades y buscador de usuarios con la cadena de seguridad real y H2 propia. Cada test crea sus
 * propios usuarios con un nombre único para no depender de los demás.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:amistades-${random.uuid}")
@WebAppConfiguration
@Import(AmistadesIntegrationTest.MutableClockConfig.class)
class AmistadesIntegrationTest {

    /** Reloj que solo avanza cuando el test lo pide, para ordenar solicitudes sin esperas. */
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-01T08:00:00Z"));

        void reset() {
            now.set(Instant.parse("2026-10-01T08:00:00Z"));
        }

        void advance(Duration duration) {
            now.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Europe/Madrid");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    /** Usuario persistido junto con un MockMvc que actúa en su nombre. */
    private record Person(Long id, String name, MockMvc mvc) {
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private FriendRequestRepository friendRequests;
    @Autowired
    private MutableClock clock;
    @Autowired
    private ObjectMapper mapper;

    private MockMvc anonymous;
    private String tag;

    @BeforeEach
    void setUp() {
        clock.reset();
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        tag = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ---- Ayudas ----

    private Person person(String name) {
        User user = users.save(new User("u-" + UUID.randomUUID() + "@example.com", name, "hash", Role.USER,
                Instant.EPOCH));
        AuthenticatedUser auth = AuthenticatedUser.from(user);
        auth.eraseCredentials();
        return new Person(user.getId(), user.getName(), TestUsers.mockMvcAs(context, auth));
    }

    private Person person() {
        return person(tag + " " + UUID.randomUUID().toString().substring(0, 8));
    }

    private void setActive(Person person, boolean active) {
        User user = users.findById(person.id()).orElseThrow();
        user.update(user.getName(), user.getRole(), active);
        users.save(user);
    }

    private static String body(Object userId) {
        return "{\"usuarioId\":" + userId + "}";
    }

    private ResultActions send(Person from, Object toId) throws Exception {
        return from.mvc().perform(post("/api/amistades/solicitudes").contentType(MediaType.APPLICATION_JSON)
                .content(body(toId)));
    }

    private long sendOk(Person from, Person to) throws Exception {
        MvcResult result = send(from, to.id()).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long befriend(Person from, Person to) throws Exception {
        long id = sendOk(from, to);
        to.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar")).andExpect(status().isNoContent());
        return id;
    }

    private ResultActions search(Person who, String q) throws Exception {
        return who.mvc().perform(get("/api/usuarios/buscar").param("q", q));
    }

    // ---- Buscar ----

    @Test
    void buscarSinSesionDa401() throws Exception {
        anonymous.perform(get("/api/usuarios/buscar").param("q", "ana")).andExpect(status().isUnauthorized());
    }

    @Test
    void buscarConQNoValidaDa400() throws Exception {
        Person me = person();
        search(me, "a").andExpect(status().isBadRequest());
        search(me, "x".repeat(101)).andExpect(status().isBadRequest());
        search(me, "   ").andExpect(status().isBadRequest());
        search(me, "").andExpect(status().isBadRequest());
        search(me, " a ").andExpect(status().isBadRequest());
        me.mvc().perform(get("/api/usuarios/buscar")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void buscarAceptaLosLimitesDeLongitud() throws Exception {
        Person me = person();
        search(me, "zz").andExpect(status().isOk());
        search(me, "x".repeat(100)).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    @Test
    void buscarNoDistingueAcentosNiMayusculas() throws Exception {
        Person me = person();
        Person jose = person("José " + tag);
        Person ines = person("INÉS Ñandú " + tag);

        search(me, "jose " + tag.toLowerCase()).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(jose.id()))
                .andExpect(jsonPath("$[0].nombre").value("José " + tag));
        search(me, "JOSÉ " + tag).andExpect(jsonPath("$", hasSize(1)));
        search(me, "ines nandu " + tag).andExpect(jsonPath("$[0].id").value(ines.id()));
        search(me, "  ines   nandu  " + tag + " ").andExpect(jsonPath("$[0].id").value(ines.id()));
    }

    @Test
    void buscarExcluyeAlPropioUsuarioYAlosInactivos() throws Exception {
        Person me = person("Yo " + tag);
        Person other = person("Otro " + tag);
        Person inactive = person("Inactivo " + tag);
        setActive(inactive, false);

        search(me, tag).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(other.id()));
    }

    @Test
    void buscarDevuelveComoMaximo20OrdenadosPorNombre() throws Exception {
        Person me = person();
        List<String> names = new ArrayList<>();
        for (int i = 24; i >= 0; i--) {
            String name = "%s Nombre %02d".formatted(tag, i);
            person(name);
            names.add(name);
        }
        List<String> firstTwenty = names.stream().sorted().limit(20).toList();

        MvcResult result = search(me, tag).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(20)))
                .andReturn();
        List<String> returned = new ArrayList<>();
        mapper.readTree(result.getResponse().getContentAsString()).forEach(n -> returned.add(n.get("nombre").asText()));
        assertThat(returned).containsExactlyElementsOf(firstTwenty);
    }

    @Test
    void buscarEscapaLosComodinesDeLike() throws Exception {
        Person me = person();
        Person percent = person("100% " + tag);
        person("100 " + tag);
        Person underscore = person("a_b" + tag);
        person("axb" + tag);

        search(me, "100%").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(percent.id()));
        search(me, "%%").andExpect(status().isOk()).andExpect(jsonPath("$[*].nombre", not(contains("Ana"))));
        search(me, "a_b" + tag).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(underscore.id()));
        search(me, "__").andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        search(me, "\\\\").andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    @Test
    void buscarSoloExponeIdYNombre() throws Exception {
        Person me = person();
        person("Visible " + tag);

        MvcResult result = search(me, tag).andExpect(status().isOk()).andReturn();
        JsonNode first = mapper.readTree(result.getResponse().getContentAsString()).get(0);
        assertThat(first.propertyNames()).containsExactlyInAnyOrder("id", "nombre");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("@example.com").doesNotContain("hash");
    }

    @Test
    void buscarUnTextoSoloDeMarcasDiacriticasNoDevuelveATodos() throws Exception {
        Person me = person();
        person("Alguien " + tag);
        search(me, "́́").andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    // ---- Enviar ----

    @Test
    void enviarSinSesionDa401() throws Exception {
        anonymous.perform(post("/api/amistades/solicitudes").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(1))).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/amistades")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/amistades/solicitudes/recibidas")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/amistades/solicitudes/enviadas")).andExpect(status().isUnauthorized());
        anonymous.perform(post("/api/amistades/solicitudes/1/aceptar").with(csrf()))
                .andExpect(status().isUnauthorized());
        anonymous.perform(post("/api/amistades/solicitudes/1/rechazar").with(csrf()))
                .andExpect(status().isUnauthorized());
        anonymous.perform(delete("/api/amistades/solicitudes/1").with(csrf())).andExpect(status().isUnauthorized());
        anonymous.perform(delete("/api/amistades/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void enviarDa201ConElCuerpoDelContrato() throws Exception {
        Person a = person();
        Person b = person();

        MvcResult result = send(a, b.id()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.usuario.id").value(b.id()))
                .andExpect(jsonPath("$.usuario.nombre").value(b.name()))
                .andExpect(jsonPath("$.fecha").value("2026-10-01T08:00:00Z"))
                .andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("id", "usuario", "fecha");
        assertThat(json.get("usuario").propertyNames()).containsExactlyInAnyOrder("id", "nombre");
        assertThat(friendRequests.count()).isPositive();
    }

    @Test
    void enviarAUnoMismoDa400() throws Exception {
        Person a = person();
        send(a, a.id()).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
    }

    @Test
    void enviarConCuerpoNoValidoDa400() throws Exception {
        Person a = person();
        send(a, "null").andExpect(status().isBadRequest());
        a.mvc().perform(post("/api/amistades/solicitudes").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        send(a, "\"abc\"").andExpect(status().isBadRequest());
    }

    @Test
    void enviarAUnUsuarioInexistenteOInactivoDa404() throws Exception {
        Person a = person();
        Person inactive = person();
        setActive(inactive, false);

        send(a, 999_999_999L).andExpect(status().isNotFound());
        send(a, inactive.id()).andExpect(status().isNotFound());
    }

    @Test
    void enviarDuplicadoDa409() throws Exception {
        Person a = person();
        Person b = person();
        sendOk(a, b);

        send(a, b.id()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("solicitud")));
    }

    @Test
    void enviarEnSentidoContrarioConUnaPendienteDa409() throws Exception {
        Person a = person();
        Person b = person();
        sendOk(a, b);

        send(b, a.id()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("solicitud")));
        b.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
    }

    @Test
    void enviarSiYaSoisAmigosDa409EnAmbosSentidos() throws Exception {
        Person a = person();
        Person b = person();
        befriend(a, b);

        send(a, b.id()).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ya sois amigos"));
        send(b, a.id()).andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ya sois amigos"));
    }

    @Test
    void enviosSimultaneosEnSentidosOpuestosDanUn201YUn409NuncaUn500() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 15; round++) {
                Person a = person();
                Person b = person();
                CyclicBarrier barrier = new CyclicBarrier(2);
                Callable<Integer> aToB = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return send(a, b.id()).andReturn().getResponse().getStatus();
                };
                Callable<Integer> bToA = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return send(b, a.id()).andReturn().getResponse().getStatus();
                };
                Future<Integer> first = pool.submit(aToB);
                Future<Integer> second = pool.submit(bToA);
                assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                        .as("ronda %d", round).containsExactlyInAnyOrder(201, 409);
                assertThat(friendRequests.findByUserLowIdAndUserHighId(Math.min(a.id(), b.id()),
                        Math.max(a.id(), b.id()))).isPresent();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- Listas ----

    @Test
    void recibidasYEnviadasSoloMuestranPendientesYLasMasRecientesPrimero() throws Exception {
        Person me = person();
        Person first = person();
        Person second = person();
        Person third = person();
        Person accepted = person();
        Person target1 = person();
        Person target2 = person();

        sendOk(first, me);
        clock.advance(Duration.ofMinutes(1));
        sendOk(second, me);
        clock.advance(Duration.ofMinutes(1));
        sendOk(third, me);
        befriend(accepted, me);
        sendOk(me, target1);
        clock.advance(Duration.ofMinutes(1));
        sendOk(me, target2);

        me.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].usuario.id", contains(third.id().intValue(), second.id().intValue(),
                        first.id().intValue())))
                .andExpect(jsonPath("$[0].usuario.nombre").value(third.name()))
                .andExpect(jsonPath("$[0].fecha").value(notNullValue()));
        me.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].usuario.id", contains(target2.id().intValue(), target1.id().intValue())));
    }

    @Test
    void lasListasSoloMuestranLoPropio() throws Exception {
        Person a = person();
        Person b = person();
        Person stranger = person();
        sendOk(a, b);

        stranger.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", empty()));
        stranger.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
        a.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", empty()));
        b.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
        a.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
        b.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
    }

    // ---- Aceptar y rechazar ----

    @Test
    void soloElDestinatarioPuedeAceptarOtroUsuarioDa404() throws Exception {
        Person a = person();
        Person b = person();
        Person stranger = person();
        long id = sendOk(a, b);

        a.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar")).andExpect(status().isNotFound());
        stranger.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar"))
                .andExpect(status().isNotFound());
        b.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
        b.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void soloElDestinatarioPuedeRechazarOtroUsuarioDa404() throws Exception {
        Person a = person();
        Person b = person();
        Person stranger = person();
        long id = sendOk(a, b);

        a.mvc().perform(post("/api/amistades/solicitudes/" + id + "/rechazar")).andExpect(status().isNotFound());
        stranger.mvc().perform(post("/api/amistades/solicitudes/" + id + "/rechazar"))
                .andExpect(status().isNotFound());
        b.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void trasAceptarLosDosVenLaAmistadOrdenadaPorNombre() throws Exception {
        Person a = person("Beatriz " + tag);
        Person b = person("Carlos " + tag);
        Person c = person("Ángela " + tag);
        befriend(a, b);
        clock.advance(Duration.ofHours(1));
        befriend(c, a);

        a.mvc().perform(get("/api/amistades")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].usuarioId").value(c.id()))
                .andExpect(jsonPath("$[0].nombre").value(c.name()))
                .andExpect(jsonPath("$[0].desde").value("2026-10-01T09:00:00Z"))
                .andExpect(jsonPath("$[1].usuarioId").value(b.id()))
                .andExpect(jsonPath("$[1].desde").value("2026-10-01T08:00:00Z"));
        b.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usuarioId").value(a.id()))
                .andExpect(jsonPath("$[0].nombre").value(a.name()));
        a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
        b.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", empty()));
    }

    @Test
    void aceptarDosVecesOUnaInexistenteDa404() throws Exception {
        Person a = person();
        Person b = person();
        long id = befriend(a, b);

        b.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar")).andExpect(status().isNotFound());
        b.mvc().perform(post("/api/amistades/solicitudes/" + id + "/rechazar")).andExpect(status().isNotFound());
        a.mvc().perform(delete("/api/amistades/solicitudes/" + id)).andExpect(status().isNotFound());
        b.mvc().perform(post("/api/amistades/solicitudes/999999999/aceptar")).andExpect(status().isNotFound());
        b.mvc().perform(post("/api/amistades/solicitudes/999999999/rechazar")).andExpect(status().isNotFound());
        b.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void rechazarYLuegoAceptarDa404YSePuedeReenviar() throws Exception {
        Person a = person();
        Person b = person();
        long id = sendOk(a, b);

        b.mvc().perform(post("/api/amistades/solicitudes/" + id + "/rechazar")).andExpect(status().isNoContent());
        b.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar")).andExpect(status().isNotFound());
        b.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
        b.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", empty()));
        a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
        assertThat(friendRequests.findById(id)).isEmpty();

        long again = sendOk(a, b);
        assertThat(again).isNotEqualTo(id);
    }

    // ---- Retirar ----

    @Test
    void soloElSolicitantePuedeRetirar() throws Exception {
        Person a = person();
        Person b = person();
        Person stranger = person();
        long id = sendOk(a, b);

        b.mvc().perform(delete("/api/amistades/solicitudes/" + id)).andExpect(status().isNotFound());
        stranger.mvc().perform(delete("/api/amistades/solicitudes/" + id)).andExpect(status().isNotFound());
        a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", hasSize(1)));

        a.mvc().perform(delete("/api/amistades/solicitudes/" + id)).andExpect(status().isNoContent());
        a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
        b.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", empty()));
        b.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar")).andExpect(status().isNotFound());
        a.mvc().perform(delete("/api/amistades/solicitudes/" + id)).andExpect(status().isNotFound());
        sendOk(a, b);
    }

    // ---- Eliminar amistad ----

    @Test
    void cualquieraDeLosDosPuedeEliminarLaAmistadYDespuesSePuedeVolverAEnviar() throws Exception {
        Person a = person();
        Person b = person();
        befriend(a, b);

        a.mvc().perform(delete("/api/amistades/" + b.id())).andExpect(status().isNoContent());
        a.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
        b.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
        sendOk(b, a);
        b.mvc().perform(delete("/api/amistades/" + a.id())).andExpect(status().isNotFound());
        a.mvc().perform(post("/api/amistades/solicitudes/"
                + mapper.readTree(a.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andReturn()
                .getResponse().getContentAsString()).get(0).get("id").asLong() + "/aceptar"))
                .andExpect(status().isNoContent());

        b.mvc().perform(delete("/api/amistades/" + a.id())).andExpect(status().isNoContent());
        a.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
        sendOk(a, b);
    }

    @Test
    void eliminarSiNoSoisAmigosDa404() throws Exception {
        Person a = person();
        Person b = person();
        Person pendingTarget = person();
        sendOk(a, pendingTarget);

        a.mvc().perform(delete("/api/amistades/" + b.id())).andExpect(status().isNotFound());
        a.mvc().perform(delete("/api/amistades/999999999")).andExpect(status().isNotFound());
        a.mvc().perform(delete("/api/amistades/" + a.id())).andExpect(status().isNotFound());
        // Una solicitud pendiente no es una amistad: no se elimina por esta ruta.
        a.mvc().perform(delete("/api/amistades/" + pendingTarget.id())).andExpect(status().isNotFound());
        pendingTarget.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", hasSize(1)));
    }

    // ---- Inactivos ----

    @Test
    void unUsuarioDesactivadoDesapareceDeLasListasDelOtroYVuelveAlReactivarlo() throws Exception {
        Person a = person();
        Person friend = person();
        Person sender = person();
        Person target = person();
        befriend(a, friend);
        sendOk(sender, a);
        sendOk(a, target);

        setActive(friend, false);
        setActive(sender, false);
        setActive(target, false);

        a.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
        a.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", empty()));
        a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
        send(a, friend.id()).andExpect(status().isNotFound());
        search(a, friend.name()).andExpect(jsonPath("$", empty()));

        setActive(friend, true);
        setActive(sender, true);
        setActive(target, true);

        a.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usuarioId").value(friend.id()));
        a.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usuario.id").value(sender.id()));
        a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].usuario.id").value(target.id()));
        search(a, friend.name()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void noSePuedeAceptarUnaSolicitudDeUnUsuarioDesactivado() throws Exception {
        Person a = person();
        Person b = person();
        long id = sendOk(a, b);
        setActive(a, false);

        b.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar")).andExpect(status().isNotFound());
        setActive(a, true);
        b.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar")).andExpect(status().isNoContent());
    }

    // ---- Errores ----

    @Test
    void losErroresSonProblemDetailSinDatosInternos() throws Exception {
        Person a = person();
        Person b = person();
        sendOk(a, b);

        List<ResultActions> errors = List.of(
                send(a, b.id()),
                send(a, a.id()),
                send(a, 999_999_999L),
                a.mvc().perform(post("/api/amistades/solicitudes/999999999/aceptar")),
                a.mvc().perform(delete("/api/amistades/" + b.id())),
                search(a, "x"));
        for (ResultActions error : errors) {
            MvcResult result = error.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.status").isNumber())
                    .andExpect(jsonPath("$.title").isString())
                    .andReturn();
            String text = result.getResponse().getContentAsString();
            assertThat(text).doesNotContainIgnoringCase("exception").doesNotContainIgnoringCase("org.")
                    .doesNotContainIgnoringCase("com.reservasalas").doesNotContainIgnoringCase("sql")
                    .doesNotContainIgnoringCase("FRIEND_REQUESTS").doesNotContainIgnoringCase("UK_")
                    .doesNotContain("@example.com").doesNotContain("\tat ").doesNotContain("at com.");
        }
    }

    // ---- Concurrencia ----

    private static final int ROUNDS = 15;

    /** Lanza dos acciones a la vez (misma barrera) y devuelve sus códigos de estado. */
    private List<Integer> race(Callable<ResultActions> first, Callable<ResultActions> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<Integer> one = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return first.call().andReturn().getResponse().getStatus();
            });
            Future<Integer> two = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return second.call().andReturn().getResponse().getStatus();
            });
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private ResultActions accept(Person who, long id) throws Exception {
        return who.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar"));
    }

    private ResultActions reject(Person who, long id) throws Exception {
        return who.mvc().perform(post("/api/amistades/solicitudes/" + id + "/rechazar"));
    }

    private ResultActions withdraw(Person who, long id) throws Exception {
        return who.mvc().perform(delete("/api/amistades/solicitudes/" + id));
    }

    private ResultActions unfriend(Person who, Person other) throws Exception {
        return who.mvc().perform(delete("/api/amistades/" + other.id()));
    }

    @Test
    void dosAceptacionesSimultaneasDanUn204YUn404() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Person a = person();
            Person b = person();
            long id = sendOk(a, b);

            assertThat(race(() -> accept(b, id), () -> accept(b, id))).as("ronda %d", round)
                    .containsExactlyInAnyOrder(204, 404);
            b.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Test
    void aceptarFrenteARechazarTieneUnSoloGanadorYEstadoCoherente() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Person a = person();
            Person b = person();
            long id = sendOk(a, b);

            List<Integer> statuses = race(() -> accept(b, id), () -> reject(b, id));

            assertThat(statuses).as("ronda %d", round).containsExactlyInAnyOrder(204, 404);
            boolean friends = !mapper.readTree(b.mvc().perform(get("/api/amistades")).andReturn().getResponse()
                    .getContentAsString()).isEmpty();
            // Si ganó aceptar son amigos; si ganó rechazar no queda ni fila.
            assertThat(friendRequests.findById(id).isPresent()).as("ronda %d", round).isEqualTo(friends);
            b.mvc().perform(get("/api/amistades/solicitudes/recibidas")).andExpect(jsonPath("$", empty()));
            a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
        }
    }

    @Test
    void aceptarFrenteARetirarTieneUnSoloGanadorYEstadoCoherente() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Person a = person();
            Person b = person();
            long id = sendOk(a, b);

            List<Integer> statuses = race(() -> accept(b, id), () -> withdraw(a, id));

            assertThat(statuses).as("ronda %d", round).containsExactlyInAnyOrder(204, 404);
            boolean friends = !mapper.readTree(b.mvc().perform(get("/api/amistades")).andReturn().getResponse()
                    .getContentAsString()).isEmpty();
            assertThat(friendRequests.findById(id).isPresent()).as("ronda %d", round).isEqualTo(friends);
            a.mvc().perform(get("/api/amistades/solicitudes/enviadas")).andExpect(jsonPath("$", empty()));
        }
    }

    @Test
    void eliminarLaAmistadDesdeLosDosLadosAlaVezDaUn204YUn404() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Person a = person();
            Person b = person();
            befriend(a, b);

            assertThat(race(() -> unfriend(a, b), () -> unfriend(b, a))).as("ronda %d", round)
                    .containsExactlyInAnyOrder(204, 404);
            a.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
            b.mvc().perform(get("/api/amistades")).andExpect(jsonPath("$", empty()));
        }
    }

    // ---- Renombrar y desactivados ----

    @Test
    void alRenombrarUnUsuarioElBuscadorEncuentraElNombreNuevoYNoElAntiguo() throws Exception {
        MockMvc admin = TestUsers.mockMvcAs(context, TestUsers.persisted(users, Role.ADMIN));
        Person me = person();
        Person target = person("Antiguo " + tag);
        String newName = "Renombrado Ñu " + tag;

        admin.perform(put("/api/usuarios/" + target.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + newName + "\",\"rol\":\"USER\",\"activo\":true}"))
                .andExpect(status().isOk());

        search(me, "renombrado nu " + tag).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(target.id()))
                .andExpect(jsonPath("$[0].nombre").value(newName));
        search(me, "antiguo " + tag).andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
    }

    @Test
    void eliminarLaAmistadOretirarUnaSolicitudConUnUsuarioDesactivadoDa204() throws Exception {
        Person a = person();
        Person friend = person();
        Person target = person();
        befriend(a, friend);
        long pending = sendOk(a, target);
        setActive(friend, false);
        setActive(target, false);

        a.mvc().perform(delete("/api/amistades/" + friend.id())).andExpect(status().isNoContent());
        withdraw(a, pending).andExpect(status().isNoContent());
        assertThat(friendRequests.findById(pending)).isEmpty();
    }
}
