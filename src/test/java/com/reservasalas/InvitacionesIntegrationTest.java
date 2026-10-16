package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.reservasalas.entity.Invitation;
import com.reservasalas.entity.InvitationStatus;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.Room;
import com.reservasalas.entity.User;
import com.reservasalas.repository.InvitationRepository;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.RoomRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.support.TestUsers;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Invitaciones a reservas y privacidad con la cadena de seguridad real, usuarios persistidos, H2
 * propia y reloj mutable. Cada test crea su sala y sus usuarios, así que no depende de los demás.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:invitaciones-${random.uuid}",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@WebAppConfiguration
@Import(InvitacionesIntegrationTest.MutableClockConfig.class)
class InvitacionesIntegrationTest {

    /** 2026-10-01 10:00 en Europe/Madrid. */
    private static final Instant START = Instant.parse("2026-10-01T08:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final LocalDate BASE = LocalDate.of(2030, 5, 1);
    private static final Set<String> BUSY_KEYS =
            Set.of("id", "salaId", "fecha", "horaInicio", "horaFin", "detalle");

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(START);

        void reset() {
            now.set(START);
        }

        void set(Instant instant) {
            now.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
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

    private record Person(Long id, String name, MockMvc mvc) {
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoomRepository rooms;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private InvitationRepository invitations;
    @Autowired
    private MutableClock clock;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc anonymous;
    private Room room;

    @BeforeEach
    void setUp() {
        clock.reset();
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        room = newRoom(10);
    }

    // ---- Ayudas ----

    private Room newRoom(int capacity) {
        return rooms.save(new Room("Sala " + UUID.randomUUID(), capacity, 1));
    }

    private User newUser(String name, Role role) {
        return users.save(new User("inv-" + UUID.randomUUID() + "@example.com", name, "hash", role,
                Instant.EPOCH));
    }

    private Person person(String name, Role role) {
        User user = newUser(name, role);
        AuthenticatedUser auth = AuthenticatedUser.from(user);
        auth.eraseCredentials();
        return new Person(user.getId(), user.getName(), TestUsers.mockMvcAs(context, auth));
    }

    private Person person(String name) {
        return person(name, Role.USER);
    }

    private Person person() {
        return person("Persona " + UUID.randomUUID().toString().substring(0, 8));
    }

    private Person admin() {
        return person("Admin", Role.ADMIN);
    }

    private void befriend(Person a, Person b) throws Exception {
        MvcResult result = a.mvc().perform(post("/api/amistades/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON).content(body(b.id())))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        b.mvc().perform(post("/api/amistades/solicitudes/" + id + "/aceptar")).andExpect(status().isNoContent());
    }

    private Person friendOf(Person organizer) throws Exception {
        Person friend = person();
        befriend(organizer, friend);
        return friend;
    }

    private static String body(Object userId) {
        return "{\"usuarioId\":" + userId + "}";
    }

    private Reservation reservation(Person organizer, Room inRoom, LocalDate date, LocalTime start, LocalTime end) {
        return reservations.save(new Reservation(inRoom, date, start, end,
                users.getReferenceById(organizer.id()), "Motivo secreto"));
    }

    private Reservation reservation(Person organizer) {
        return reservation(organizer, room, BASE, LocalTime.of(9, 0), LocalTime.of(10, 0));
    }

    private ResultActions invite(Person from, Long reservationId, Object userId) throws Exception {
        return from.mvc().perform(post("/api/reservas/" + reservationId + "/invitaciones")
                .contentType(MediaType.APPLICATION_JSON).content(body(userId)));
    }

    private long inviteOk(Person from, Long reservationId, Person guest) throws Exception {
        MvcResult result = invite(from, reservationId, guest.id()).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Invitation insertInvitation(Reservation reservation, Person guest, InvitationStatus status) {
        Invitation invitation = new Invitation(reservation, users.getReferenceById(guest.id()));
        if (status == InvitationStatus.ACCEPTED) {
            invitation.accept();
        } else if (status == InvitationStatus.REJECTED) {
            invitation.reject();
        }
        return invitations.save(invitation);
    }

    private void accept(Person guest, long invitationId) throws Exception {
        guest.mvc().perform(post("/api/invitaciones/" + invitationId + "/aceptar"))
                .andExpect(status().isNoContent());
    }

    private void reject(Person guest, long invitationId) throws Exception {
        guest.mvc().perform(post("/api/invitaciones/" + invitationId + "/rechazar"))
                .andExpect(status().isNoContent());
    }

    private JsonNode detail(Person who, Long reservationId) throws Exception {
        String json = who.mvc().perform(get("/api/reservas/" + reservationId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode list(Person who, String query) throws Exception {
        String json = who.mvc().perform(get("/api/reservas" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new HashSet<>();
        node.propertyNames().forEach(keys::add);
        return keys;
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(r -> ids.add(r.get("id").asLong()));
        return ids;
    }

    private void assertBusy(JsonNode node) {
        assertThat(keys(node)).isEqualTo(BUSY_KEYS);
        assertThat(node.get("detalle").asString()).isEqualTo("OCUPADA");
    }

    private void assertFull(JsonNode node) {
        assertThat(node.get("detalle").asString()).isEqualTo("COMPLETO");
        assertThat(node.has("usuarioId")).isTrue();
        assertThat(node.has("responsable")).isTrue();
        assertThat(node.get("motivo").asString()).isEqualTo("Motivo secreto");
    }

    // ---- Privacidad ----

    @Test
    void ocupadaTieneExactamenteSeisClavesEnDetalleYListado() throws Exception {
        Person org = person();
        Person stranger = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        inviteOk(org, r.getId(), guest);

        JsonNode detail = detail(stranger, r.getId());
        assertBusy(detail);
        assertThat(detail.get("id").asLong()).isEqualTo(r.getId());
        assertThat(detail.get("salaId").asLong()).isEqualTo(room.getId());
        assertThat(detail.get("fecha").asString()).isEqualTo(BASE.toString());
        assertThat(detail.get("horaInicio").asString()).isEqualTo("09:00");
        assertThat(detail.get("horaFin").asString()).isEqualTo("10:00");

        JsonNode inList = list(stranger, "?salaId=" + room.getId()).get("content").get(0);
        assertBusy(inList);
    }

    @Test
    void ocupadaNoFiltraNiNombreNiMotivoEnElTextoDeLaRespuesta() throws Exception {
        Person org = person("Organizadora Reservada");
        Person stranger = person();
        Reservation r = reservation(org);

        String raw = stranger.mvc().perform(get("/api/reservas/" + r.getId())).andReturn().getResponse()
                .getContentAsString();
        assertThat(raw).doesNotContain("Motivo secreto").doesNotContain("Organizadora Reservada")
                .doesNotContain("null");
    }

    @Test
    void completoParaOrganizadorInvitadoPendienteAceptadoYAdmin() throws Exception {
        Person org = person();
        Person pending = friendOf(org);
        Person accepted = friendOf(org);
        Person adm = admin();
        Reservation r = reservation(org);
        inviteOk(org, r.getId(), pending);
        long acceptedInvitation = inviteOk(org, r.getId(), accepted);
        accept(accepted, acceptedInvitation);

        for (Person who : List.of(org, pending, accepted, adm)) {
            JsonNode detail = detail(who, r.getId());
            assertFull(detail);
            assertThat(detail.get("usuarioId").asLong()).isEqualTo(org.id());
            assertThat(detail.get("responsable").asString()).isEqualTo(org.name());
            assertThat(detail.get("invitaciones")).hasSize(2);
        }
    }

    @Test
    void elInvitadoQueRechazaDejaDeVerLaReservaCompleta() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);
        assertFull(detail(guest, r.getId()));

        reject(guest, invitation);

        assertBusy(detail(guest, r.getId()));
        assertBusy(list(guest, "?salaId=" + room.getId()).get("content").get(0));
    }

    @Test
    void elInvitadoAceptadoQueRechazaTambienDejaDeVerlaCompleta() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);
        accept(guest, invitation);
        reject(guest, invitation);

        assertBusy(detail(guest, r.getId()));
    }

    @Test
    void detalleDeReservaInexistenteDa404() throws Exception {
        person().mvc().perform(get("/api/reservas/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void detalleCompletoOrdenaLasInvitacionesPorNombre() throws Exception {
        Person org = person();
        Reservation r = reservation(org);
        Person carla = person("Carla");
        Person ana = person("Ana");
        Person bea = person("Bea");
        for (Person p : List.of(carla, ana, bea)) {
            befriend(org, p);
            inviteOk(org, r.getId(), p);
        }

        JsonNode invitaciones = detail(org, r.getId()).get("invitaciones");
        List<String> names = new ArrayList<>();
        invitaciones.forEach(i -> names.add(i.get("nombre").asString()));
        assertThat(names).containsExactly("Ana", "Bea", "Carla");
        invitaciones.forEach(i -> {
            assertThat(i.get("reservaId").asLong()).isEqualTo(r.getId());
            assertThat(i.get("estado").asString()).isEqualTo("PENDIENTE");
        });
    }

    @Test
    void detalleCompletoSinInvitacionesDevuelveListaVacia() throws Exception {
        Person org = person();
        Reservation r = reservation(org);
        assertThat(detail(org, r.getId()).get("invitaciones")).isEmpty();
    }

    @Test
    void elListadoAplicaLaPrivacidadPorElemento() throws Exception {
        Person org = person();
        Person other = person();
        Person viewer = person();
        Person adm = admin();
        befriend(org, viewer);
        Reservation own = reservation(viewer, room, BASE, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Reservation invited = reservation(org, room, BASE, LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation stranger = reservation(other, room, BASE, LocalTime.of(10, 0), LocalTime.of(11, 0));
        Reservation rejected = reservation(org, room, BASE, LocalTime.of(11, 0), LocalTime.of(12, 0));
        inviteOk(org, invited.getId(), viewer);
        reject(viewer, inviteOk(org, rejected.getId(), viewer));

        JsonNode content = list(viewer, "?salaId=" + room.getId()).get("content");
        assertThat(content).hasSize(4);
        assertFull(content.get(0));
        assertThat(content.get(0).get("id").asLong()).isEqualTo(own.getId());
        assertFull(content.get(1));
        assertThat(content.get(1).get("id").asLong()).isEqualTo(invited.getId());
        assertBusy(content.get(2));
        assertThat(content.get(2).get("id").asLong()).isEqualTo(stranger.getId());
        assertBusy(content.get(3));
        assertThat(content.get(3).get("id").asLong()).isEqualTo(rejected.getId());
        content.forEach(r -> assertThat(r.has("invitaciones")).isFalse());

        JsonNode asAdmin = list(adm, "?salaId=" + room.getId()).get("content");
        assertThat(asAdmin).hasSize(4);
        asAdmin.forEach(r -> assertThat(r.get("detalle").asString()).isEqualTo("COMPLETO"));
    }

    @Test
    void misDevuelveSoloOrganizadasPendientesYAceptadasSiempreCompletas() throws Exception {
        Person org = person();
        Person viewer = person();
        befriend(org, viewer);
        Reservation own = reservation(viewer, room, BASE, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Reservation pending = reservation(org, room, BASE.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation accepted = reservation(org, room, BASE.plusDays(2), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation rejected = reservation(org, room, BASE.plusDays(3), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation none = reservation(org, room, BASE.plusDays(4), LocalTime.of(9, 0), LocalTime.of(10, 0));
        inviteOk(org, pending.getId(), viewer);
        accept(viewer, inviteOk(org, accepted.getId(), viewer));
        reject(viewer, inviteOk(org, rejected.getId(), viewer));

        JsonNode page = list(viewer, "?mis=true&salaId=" + room.getId());
        assertThat(ids(page)).containsExactly(own.getId(), pending.getId(), accepted.getId());
        page.get("content").forEach(r -> {
            assertFull(r);
            assertThat(r.has("invitaciones")).isFalse();
        });
        assertThat(page.get("totalElements").asInt()).isEqualTo(3);
        assertThat(ids(page)).doesNotContain(rejected.getId(), none.getId());
    }

    @Test
    void misSeCombinaConFiltrosYPaginacion() throws Exception {
        Person org = person();
        Person viewer = person();
        befriend(org, viewer);
        Room otherRoom = newRoom(10);
        List<Reservation> mine = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Reservation r = reservation(org, room, BASE.plusDays(i), LocalTime.of(9, 0), LocalTime.of(10, 0));
            inviteOk(org, r.getId(), viewer);
            mine.add(r);
        }
        Reservation inOtherRoom = reservation(viewer, otherRoom, BASE.plusDays(1), LocalTime.of(9, 0),
                LocalTime.of(10, 0));
        reservation(org, room, BASE.plusDays(9), LocalTime.of(9, 0), LocalTime.of(10, 0));

        JsonNode byRoom = list(viewer, "?mis=true&salaId=" + otherRoom.getId());
        assertThat(ids(byRoom)).containsExactly(inOtherRoom.getId());

        JsonNode byDates = list(viewer, "?mis=true&salaId=" + room.getId() + "&fechaDesde=" + BASE.plusDays(1)
                + "&fechaHasta=" + BASE.plusDays(3));
        assertThat(ids(byDates)).containsExactly(mine.get(1).getId(), mine.get(2).getId(), mine.get(3).getId());

        JsonNode page0 = list(viewer, "?mis=true&salaId=" + room.getId() + "&size=2&page=0");
        JsonNode page2 = list(viewer, "?mis=true&salaId=" + room.getId() + "&size=2&page=2");
        assertThat(ids(page0)).containsExactly(mine.get(0).getId(), mine.get(1).getId());
        assertThat(ids(page2)).containsExactly(mine.get(4).getId());
        assertThat(page0.get("totalElements").asInt()).isEqualTo(5);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    void misSinNadaDevuelvePaginaVacia() throws Exception {
        person().mvc().perform(get("/api/reservas").param("mis", "true").param("salaId", room.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void misDeUnAdminNoIncluyeLasAjenasSinInvitacion() throws Exception {
        Person adm = admin();
        Person org = person();
        reservation(org);
        assertThat(list(adm, "?mis=true&salaId=" + room.getId()).get("content")).isEmpty();
    }

    // ---- Invitar ----

    @Test
    void invitarDa201ConElCuerpoDelContrato() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);

        invite(org, r.getId(), guest.id())
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.reservaId").value(r.getId()))
                .andExpect(jsonPath("$.usuarioId").value(guest.id()))
                .andExpect(jsonPath("$.nombre").value(guest.name()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void invitarSinSesionDa401() throws Exception {
        anonymous.perform(post("/api/reservas/1/invitaciones").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body(1))).andExpect(status().isUnauthorized());
    }

    @Test
    void invitarSinUsuarioIdDa400() throws Exception {
        Person org = person();
        Reservation r = reservation(org);
        org.mvc().perform(post("/api/reservas/" + r.getId() + "/invitaciones")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void invitarAUnaReservaInexistenteDa404() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        invite(org, 999999999L, guest.id()).andExpect(status().isNotFound());
        // El 404 prevalece sobre la invitación a uno mismo
        invite(org, 999999999L, org.id()).andExpect(status().isNotFound());
    }

    @Test
    void invitarSiNoEresElOrganizadorDa403TambienParaAdmin() throws Exception {
        Person org = person();
        Person other = person();
        Person adm = admin();
        Person guest = friendOf(org);
        befriend(other, guest);
        befriend(adm, guest);
        Reservation r = reservation(org);

        invite(other, r.getId(), guest.id()).andExpect(status().isForbidden());
        invite(adm, r.getId(), guest.id()).andExpect(status().isForbidden());
        // El 403 prevalece sobre la invitación a uno mismo
        invite(other, r.getId(), other.id()).andExpect(status().isForbidden());
        assertThat(invitations.findByReservationIdAndUserId(r.getId(), guest.id())).isEmpty();
    }

    @Test
    void invitarseAUnoMismoDa400() throws Exception {
        Person org = person();
        Reservation r = reservation(org);
        invite(org, r.getId(), org.id()).andExpect(status().isBadRequest());
    }

    @Test
    void invitarseAUnoMismoEnUnaReservaPasadaSigueDando400() throws Exception {
        Person org = person();
        Reservation r = reservation(org, room, LocalDate.of(2020, 1, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));
        invite(org, r.getId(), org.id()).andExpect(status().isBadRequest());
    }

    @Test
    void invitarANoAmigoDa409() throws Exception {
        Person org = person();
        Person stranger = person();
        Reservation r = reservation(org);
        invite(org, r.getId(), stranger.id()).andExpect(status().isConflict());
    }

    @Test
    void invitarAUnaSolicitudDeAmistadPendienteDa409() throws Exception {
        Person org = person();
        Person other = person();
        org.mvc().perform(post("/api/amistades/solicitudes").contentType(MediaType.APPLICATION_JSON)
                .content(body(other.id()))).andExpect(status().isCreated());
        Reservation r = reservation(org);
        invite(org, r.getId(), other.id()).andExpect(status().isConflict());
    }

    @Test
    void invitarAUsuarioInexistenteDa409() throws Exception {
        Person org = person();
        Reservation r = reservation(org);
        invite(org, r.getId(), 999999999L).andExpect(status().isConflict());
    }

    @Test
    void invitarAUnAmigoDesactivadoDa409() throws Exception {
        Person org = person();
        Person friend = friendOf(org);
        User user = users.findById(friend.id()).orElseThrow();
        user.update(user.getName(), user.getRole(), false);
        users.save(user);
        Reservation r = reservation(org);
        invite(org, r.getId(), friend.id()).andExpect(status().isConflict());
    }

    @Test
    void invitarDosVecesDa409TantoPendienteComoAceptada() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        invite(org, r.getId(), guest.id()).andExpect(status().isConflict());
        accept(guest, invitation);
        invite(org, r.getId(), guest.id()).andExpect(status().isConflict());
        assertThat(invitations.countActiveByReservation(r.getId())).isEqualTo(1);
    }

    @Test
    void elAforoIncluyeAlOrganizadorYUnRechazoLiberaPlaza() throws Exception {
        Room small = newRoom(3);
        Person org = person();
        Person g1 = friendOf(org);
        Person g2 = friendOf(org);
        Person g3 = friendOf(org);
        Reservation r = reservation(org, small, BASE, LocalTime.of(9, 0), LocalTime.of(10, 0));

        long first = inviteOk(org, r.getId(), g1);
        long second = inviteOk(org, r.getId(), g2);
        invite(org, r.getId(), g3.id()).andExpect(status().isConflict());

        accept(g1, first);
        invite(org, r.getId(), g3.id()).andExpect(status().isConflict());

        reject(g2, second);
        invite(org, r.getId(), g3.id()).andExpect(status().isCreated());
        assertThat(invitations.countActiveByReservation(r.getId())).isEqualTo(2);
    }

    @Test
    void conCapacidadUnoNoSePuedeInvitarANadie() throws Exception {
        Room single = newRoom(1);
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org, single, BASE, LocalTime.of(9, 0), LocalTime.of(10, 0));
        invite(org, r.getId(), guest.id()).andExpect(status().isConflict());
    }

    @Test
    void retirarUnaInvitacionLiberaPlaza() throws Exception {
        Room small = newRoom(2);
        Person org = person();
        Person g1 = friendOf(org);
        Person g2 = friendOf(org);
        Reservation r = reservation(org, small, BASE, LocalTime.of(9, 0), LocalTime.of(10, 0));
        long invitation = inviteOk(org, r.getId(), g1);
        accept(g1, invitation);
        invite(org, r.getId(), g2.id()).andExpect(status().isConflict());

        org.mvc().perform(delete("/api/reservas/" + r.getId() + "/invitaciones/" + invitation))
                .andExpect(status().isNoContent());

        invite(org, r.getId(), g2.id()).andExpect(status().isCreated());
    }

    @Test
    void invitarAUnaReservaPasadaDa409() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org, room, BASE, LocalTime.of(9, 0), LocalTime.of(10, 0));
        inviteOk(org, r.getId(), friendOf(org));

        clock.set(BASE.atTime(10, 0).atZone(ZONE).toInstant());

        invite(org, r.getId(), guest.id()).andExpect(status().isConflict());
    }

    @Test
    void invitarMientrasLaReservaEstaEnCursoEsValido() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org, room, TODAY, LocalTime.of(10, 0), LocalTime.of(10, 1));
        // Ahora son las 10:00: la reserva termina a las 10:01
        invite(org, r.getId(), guest.id()).andExpect(status().isCreated());
    }

    @Test
    void invitarCuandoFechaMasHoraFinIgualaAhoraDa409() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org, room, TODAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        invite(org, r.getId(), guest.id()).andExpect(status().isConflict());
    }

    @Test
    void reinvitarAQuienRechazoDa201YVuelveAPendienteReutilizandoLaFila() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long first = inviteOk(org, r.getId(), guest);
        reject(guest, first);

        MvcResult again = invite(org, r.getId(), guest.id()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE")).andReturn();

        assertThat(mapper.readTree(again.getResponse().getContentAsString()).get("id").asLong()).isEqualTo(first);
        assertThat(invitations.findAll().stream().filter(i -> i.getReservation().getId().equals(r.getId())))
                .hasSize(1);
        assertFull(detail(guest, r.getId()));
        guest.mvc().perform(get("/api/invitaciones/pendientes"))
                .andExpect(jsonPath("$[?(@.id==" + first + ")]", hasSize(1)));
    }

    @Test
    void reinvitarAQuienRechazoSigueRespetandoElAforo() throws Exception {
        Room small = newRoom(2);
        Person org = person();
        Person g1 = friendOf(org);
        Person g2 = friendOf(org);
        Reservation r = reservation(org, small, BASE, LocalTime.of(9, 0), LocalTime.of(10, 0));
        reject(g1, inviteOk(org, r.getId(), g1));
        inviteOk(org, r.getId(), g2);

        invite(org, r.getId(), g1.id()).andExpect(status().isConflict());
    }

    // ---- Concurrencia ----

    private List<Integer> concurrentInvites(Person org, Long reservationId, List<Person> guests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(guests.size());
        try {
            CyclicBarrier barrier = new CyclicBarrier(guests.size());
            List<Future<Integer>> futures = new ArrayList<>();
            for (Person guest : guests) {
                Callable<Integer> task = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return invite(org, reservationId, guest.id()).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void dosInvitacionesSimultaneasConUnaPlazaLibreDanUn201YUn409() throws Exception {
        Room small = newRoom(2);
        Person org = person();
        Person g1 = friendOf(org);
        Person g2 = friendOf(org);
        for (int round = 0; round < 8; round++) {
            Reservation r = reservation(org, small, BASE.plusDays(round), LocalTime.of(9, 0), LocalTime.of(10, 0));

            List<Integer> statuses = concurrentInvites(org, r.getId(), List.of(g1, g2));

            assertThat(statuses).as("ronda %d", round).containsExactlyInAnyOrder(201, 409);
            assertThat(invitations.countActiveByReservation(r.getId())).isEqualTo(1);
        }
    }

    @Test
    void variasInvitacionesSimultaneasNuncaSuperanElAforo() throws Exception {
        Room small = newRoom(3);
        Person org = person();
        List<Person> guests = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            guests.add(friendOf(org));
        }
        for (int round = 0; round < 4; round++) {
            Reservation r = reservation(org, small, BASE.plusDays(round), LocalTime.of(9, 0), LocalTime.of(10, 0));

            List<Integer> statuses = concurrentInvites(org, r.getId(), guests);

            assertThat(statuses.stream().filter(s -> s == 201).count()).as("ronda %d", round).isEqualTo(2);
            assertThat(statuses.stream().filter(s -> s == 409).count()).as("ronda %d", round).isEqualTo(3);
            assertThat(invitations.countActiveByReservation(r.getId())).isEqualTo(2);
        }
    }

    @Test
    void invitarAlMismoUsuarioEnParaleloDaUn201YElRestoEs409() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        for (int round = 0; round < 4; round++) {
            Reservation r = reservation(org, room, BASE.plusDays(round), LocalTime.of(9, 0), LocalTime.of(10, 0));

            List<Integer> statuses = concurrentInvites(org, r.getId(), List.of(guest, guest, guest));

            assertThat(statuses).as("ronda %d", round).containsExactlyInAnyOrder(201, 409, 409);
            assertThat(invitations.countActiveByReservation(r.getId())).isEqualTo(1);
        }
    }

    // ---- Aceptar y rechazar ----

    @Test
    void aceptarCambiaElEstadoYAparecePendienteDejaDeAparecer() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        accept(guest, invitation);

        JsonNode invitaciones = detail(org, r.getId()).get("invitaciones");
        assertThat(invitaciones.get(0).get("estado").asString()).isEqualTo("ACEPTADA");
        guest.mvc().perform(get("/api/invitaciones/pendientes")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void soloElInvitadoPuedeResponderOtroUsuarioDa404() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Person other = person();
        Person adm = admin();
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        for (Person who : List.of(other, org, adm)) {
            who.mvc().perform(post("/api/invitaciones/" + invitation + "/aceptar")).andExpect(status().isNotFound());
            who.mvc().perform(post("/api/invitaciones/" + invitation + "/rechazar")).andExpect(status().isNotFound());
        }
        assertThat(invitations.findById(invitation).orElseThrow().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void responderUnaInvitacionInexistenteDa404() throws Exception {
        Person guest = person();
        guest.mvc().perform(post("/api/invitaciones/999999999/aceptar")).andExpect(status().isNotFound());
        guest.mvc().perform(post("/api/invitaciones/999999999/rechazar")).andExpect(status().isNotFound());
    }

    @Test
    void responderSinSesionDa401() throws Exception {
        anonymous.perform(post("/api/invitaciones/1/aceptar").with(csrf())).andExpect(status().isUnauthorized());
        anonymous.perform(post("/api/invitaciones/1/rechazar").with(csrf())).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/invitaciones/pendientes")).andExpect(status().isUnauthorized());
    }

    @Test
    void aceptarDosVecesDa409() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);
        accept(guest, invitation);

        guest.mvc().perform(post("/api/invitaciones/" + invitation + "/aceptar")).andExpect(status().isConflict());
    }

    @Test
    void aceptarUnaInvitacionRechazadaDa409() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);
        reject(guest, invitation);

        guest.mvc().perform(post("/api/invitaciones/" + invitation + "/aceptar")).andExpect(status().isConflict());
    }

    @Test
    void rechazarTrasAceptarDa204() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);
        accept(guest, invitation);

        reject(guest, invitation);

        assertThat(detail(org, r.getId()).get("invitaciones").get(0).get("estado").asString())
                .isEqualTo("RECHAZADA");
    }

    @Test
    void rechazarDosVecesDa409() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);
        reject(guest, invitation);

        guest.mvc().perform(post("/api/invitaciones/" + invitation + "/rechazar")).andExpect(status().isConflict());
    }

    @Test
    void aceptarUnaReservaPasadaDa409() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        clock.set(BASE.atTime(10, 0).atZone(ZONE).toInstant());

        guest.mvc().perform(post("/api/invitaciones/" + invitation + "/aceptar")).andExpect(status().isConflict());
        guest.mvc().perform(post("/api/invitaciones/" + invitation + "/rechazar")).andExpect(status().isConflict());
        assertThat(invitations.findById(invitation).orElseThrow().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void aceptarUnMinutoAntesDelFinSiguePermitido() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        clock.set(BASE.atTime(9, 59).atZone(ZONE).toInstant());

        accept(guest, invitation);
    }

    @Test
    void laHoraDeFinSeEvaluaEnLaZonaVigenteYNoEnUtc() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        // 2030-05-01 09:30 UTC = 11:30 en Madrid (verano), ya pasada aunque en UTC no lo estaría
        clock.set(Instant.parse("2030-05-01T09:30:00Z"));

        guest.mvc().perform(post("/api/invitaciones/" + invitation + "/aceptar")).andExpect(status().isConflict());
    }

    // ---- Retirar ----

    @Test
    void retirarDa204YElInvitadoYaNoVeLaReservaCompletaNiLaInvitacion() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        org.mvc().perform(delete("/api/reservas/" + r.getId() + "/invitaciones/" + invitation))
                .andExpect(status().isNoContent());

        assertThat(invitations.findById(invitation)).isEmpty();
        assertBusy(detail(guest, r.getId()));
        guest.mvc().perform(get("/api/invitaciones/pendientes")).andExpect(jsonPath("$", hasSize(0)));
        guest.mvc().perform(post("/api/invitaciones/" + invitation + "/aceptar")).andExpect(status().isNotFound());
        invite(org, r.getId(), guest.id()).andExpect(status().isCreated());
    }

    @Test
    void retirarSiNoEresElOrganizadorDa403() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Person other = person();
        Person adm = admin();
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        for (Person who : List.of(guest, other, adm)) {
            who.mvc().perform(delete("/api/reservas/" + r.getId() + "/invitaciones/" + invitation))
                    .andExpect(status().isForbidden());
        }
        assertThat(invitations.findById(invitation)).isPresent();
    }

    @Test
    void retirarConReservaOInvitacionInexistenteDa404() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        Reservation otherReservation = reservation(org, room, BASE.plusDays(1), LocalTime.of(9, 0),
                LocalTime.of(10, 0));
        long invitation = inviteOk(org, r.getId(), guest);

        org.mvc().perform(delete("/api/reservas/999999999/invitaciones/" + invitation))
                .andExpect(status().isNotFound());
        org.mvc().perform(delete("/api/reservas/" + r.getId() + "/invitaciones/999999999"))
                .andExpect(status().isNotFound());
        // Una invitación de otra reserva no se puede retirar por esta
        org.mvc().perform(delete("/api/reservas/" + otherReservation.getId() + "/invitaciones/" + invitation))
                .andExpect(status().isNotFound());
        assertThat(invitations.findById(invitation)).isPresent();
    }

    @Test
    void retirarSinSesionDa401() throws Exception {
        anonymous.perform(delete("/api/reservas/1/invitaciones/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    // ---- Pendientes ----

    @Test
    void pendientesSoloListaLasNoPasadasOrdenadasConLaReservaCompletaYSinInvitaciones() throws Exception {
        Person org = person();
        Person guest = person();
        Reservation late = reservation(org, room, BASE.plusDays(2), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation early = reservation(org, room, BASE, LocalTime.of(15, 0), LocalTime.of(16, 0));
        Reservation earlier = reservation(org, room, BASE, LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation endedToday = reservation(org, room, TODAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation runningToday = reservation(org, room, TODAY, LocalTime.of(10, 0), LocalTime.of(10, 1));
        Reservation past = reservation(org, room, LocalDate.of(2020, 1, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation accepted = reservation(org, room, BASE.plusDays(5), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation rejected = reservation(org, room, BASE.plusDays(6), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation notMine = reservation(org, room, BASE.plusDays(7), LocalTime.of(9, 0), LocalTime.of(10, 0));
        insertInvitation(late, guest, InvitationStatus.PENDING);
        insertInvitation(early, guest, InvitationStatus.PENDING);
        insertInvitation(earlier, guest, InvitationStatus.PENDING);
        insertInvitation(endedToday, guest, InvitationStatus.PENDING);
        insertInvitation(runningToday, guest, InvitationStatus.PENDING);
        insertInvitation(past, guest, InvitationStatus.PENDING);
        insertInvitation(accepted, guest, InvitationStatus.ACCEPTED);
        insertInvitation(rejected, guest, InvitationStatus.REJECTED);
        insertInvitation(notMine, person(), InvitationStatus.PENDING);

        String json = guest.mvc().perform(get("/api/invitaciones/pendientes")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode array = mapper.readTree(json);

        List<Long> reservationIds = new ArrayList<>();
        array.forEach(i -> reservationIds.add(i.get("reserva").get("id").asLong()));
        assertThat(reservationIds).containsExactly(runningToday.getId(), earlier.getId(), early.getId(),
                late.getId());
        array.forEach(i -> {
            assertThat(i.get("id").isNumber()).isTrue();
            assertThat(keys(i)).containsExactlyInAnyOrder("id", "reserva");
            assertFull(i.get("reserva"));
            assertThat(i.get("reserva").has("invitaciones")).isFalse();
        });
    }

    @Test
    void pendientesSinInvitacionesDevuelveListaVacia() throws Exception {
        person().mvc().perform(get("/api/invitaciones/pendientes"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void pendientesDeUnAdminSoloIncluyeLasSuyas() throws Exception {
        Person org = person();
        Person adm = admin();
        Reservation r = reservation(org);
        insertInvitation(r, person(), InvitationStatus.PENDING);
        adm.mvc().perform(get("/api/invitaciones/pendientes")).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- Cascada ----

    @Test
    void cancelarLaReservaBorraSusInvitaciones() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Person accepted = friendOf(org);
        Reservation r = reservation(org);
        long pendingId = inviteOk(org, r.getId(), guest);
        long acceptedId = inviteOk(org, r.getId(), accepted);
        accept(accepted, acceptedId);
        guest.mvc().perform(get("/api/invitaciones/pendientes")).andExpect(jsonPath("$", hasSize(1)));

        org.mvc().perform(delete("/api/reservas/" + r.getId())).andExpect(status().isNoContent());

        guest.mvc().perform(get("/api/invitaciones/pendientes")).andExpect(jsonPath("$", hasSize(0)));
        assertThat(invitations.findById(pendingId)).isEmpty();
        assertThat(invitations.findById(acceptedId)).isEmpty();
        guest.mvc().perform(post("/api/invitaciones/" + pendingId + "/aceptar")).andExpect(status().isNotFound());
        guest.mvc().perform(get("/api/reservas/" + r.getId())).andExpect(status().isNotFound());
    }

    @Test
    void unAdminPuedeCancelarUnaReservaConInvitacionesYSeBorranEnCascada() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        Reservation r = reservation(org);
        long invitation = inviteOk(org, r.getId(), guest);

        admin().mvc().perform(delete("/api/reservas/" + r.getId())).andExpect(status().isNoContent());

        assertThat(invitations.findById(invitation)).isEmpty();
    }

    @Test
    void eliminarUnaSalaConReservasPasadasConInvitacionesDa204SinErrorDeIntegridad() throws Exception {
        Room doomed = newRoom(10);
        Person org = person();
        Person g1 = person();
        Person g2 = person();
        Reservation past = reservation(org, doomed, LocalDate.of(2020, 1, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Reservation past2 = reservation(org, doomed, LocalDate.of(2020, 1, 2), LocalTime.of(9, 0), LocalTime.of(10, 0));
        long i1 = insertInvitation(past, g1, InvitationStatus.ACCEPTED).getId();
        long i2 = insertInvitation(past, g2, InvitationStatus.PENDING).getId();
        long i3 = insertInvitation(past2, g1, InvitationStatus.REJECTED).getId();

        admin().mvc().perform(delete("/api/salas/" + doomed.getId())).andExpect(status().isNoContent());

        assertThat(rooms.findById(doomed.getId())).isEmpty();
        assertThat(reservations.findById(past.getId())).isEmpty();
        assertThat(invitations.findAllById(List.of(i1, i2, i3))).isEmpty();
        assertThat(users.findById(g1.id())).isPresent();
    }

    // ---- Concurrencia cruzada ----

    private List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CyclicBarrier barrier = new CyclicBarrier(tasks.size());
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private static int statusOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getStatus();
    }

    @Test
    void retirarFrenteAAceptarLaMismaInvitacionNuncaDa500YLaInvitacionNoExiste() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        for (int round = 0; round < 10; round++) {
            Reservation r = reservation(org, room, BASE.plusDays(round), LocalTime.of(9, 0), LocalTime.of(10, 0));
            long id = insertInvitation(r, guest, InvitationStatus.PENDING).getId();

            List<Integer> statuses = runConcurrently(List.of(
                    () -> statusOf(org.mvc().perform(delete("/api/reservas/" + r.getId() + "/invitaciones/" + id))),
                    () -> statusOf(guest.mvc().perform(post("/api/invitaciones/" + id + "/aceptar")))));

            assertThat(statuses.get(0)).as("retirar, ronda %d", round).isEqualTo(204);
            assertThat(statuses.get(1)).as("aceptar, ronda %d", round).isIn(204, 404, 409);
            assertThat(invitations.findById(id)).as("ronda %d", round).isEmpty();
        }
    }

    @Test
    void invitarFrenteARechazarUnaAceptadaConElAforoLlenoNuncaSuperaElAforo() throws Exception {
        Room small = newRoom(3);
        Person org = person();
        Person g1 = friendOf(org);
        Person g2 = friendOf(org);
        Person g3 = friendOf(org);
        for (int round = 0; round < 10; round++) {
            Reservation r = reservation(org, small, BASE.plusDays(round), LocalTime.of(9, 0), LocalTime.of(10, 0));
            long first = insertInvitation(r, g1, InvitationStatus.ACCEPTED).getId();
            insertInvitation(r, g2, InvitationStatus.ACCEPTED);

            List<Integer> statuses = runConcurrently(List.of(
                    () -> statusOf(invite(org, r.getId(), g3.id())),
                    () -> statusOf(g1.mvc().perform(post("/api/invitaciones/" + first + "/rechazar")))));

            assertThat(statuses.get(0)).as("invitar, ronda %d", round).isIn(201, 409);
            assertThat(statuses.get(1)).as("rechazar, ronda %d", round).isEqualTo(204);
            long active = invitations.countActiveByReservation(r.getId());
            assertThat(active).as("ronda %d", round).isLessThanOrEqualTo(2);
            assertThat(active).as("ronda %d", round).isEqualTo(statuses.get(0) == 201 ? 2 : 1);
        }
    }

    @Test
    void invitarFrenteAAceptarNuncaSuperaElAforo() throws Exception {
        Room small = newRoom(3);
        Person org = person();
        Person g1 = friendOf(org);
        Person g2 = friendOf(org);
        Person g3 = friendOf(org);
        for (int round = 0; round < 10; round++) {
            Reservation r = reservation(org, small, BASE.plusDays(round), LocalTime.of(9, 0), LocalTime.of(10, 0));
            long pending = insertInvitation(r, g1, InvitationStatus.PENDING).getId();

            List<Integer> statuses = runConcurrently(List.of(
                    () -> statusOf(invite(org, r.getId(), g2.id())),
                    () -> statusOf(invite(org, r.getId(), g3.id())),
                    () -> statusOf(g1.mvc().perform(post("/api/invitaciones/" + pending + "/aceptar")))));

            assertThat(statuses.subList(0, 2)).as("ronda %d", round).containsExactlyInAnyOrder(201, 409);
            assertThat(statuses.get(2)).as("aceptar, ronda %d", round).isEqualTo(204);
            assertThat(invitations.countActiveByReservation(r.getId())).as("ronda %d", round).isEqualTo(2);
            assertThat(invitations.findById(pending).orElseThrow().getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        }
    }

    @Test
    void cancelarFrenteAInvitarNoDejaInvitacionesHuerfanas() throws Exception {
        Person org = person();
        Person guest = friendOf(org);
        for (int round = 0; round < 10; round++) {
            Reservation r = reservation(org, room, BASE.plusDays(round), LocalTime.of(9, 0), LocalTime.of(10, 0));

            List<Integer> statuses = runConcurrently(List.of(
                    () -> statusOf(org.mvc().perform(delete("/api/reservas/" + r.getId()))),
                    () -> statusOf(invite(org, r.getId(), guest.id()))));

            assertThat(statuses.get(0)).as("cancelar, ronda %d", round).isEqualTo(204);
            assertThat(statuses.get(1)).as("invitar, ronda %d", round).isIn(201, 404);
            assertThat(reservations.findById(r.getId())).as("ronda %d", round).isEmpty();
            assertThat(invitations.findByReservationIdAndUserId(r.getId(), guest.id()))
                    .as("ronda %d", round).isEmpty();
            guest.mvc().perform(get("/api/invitaciones/pendientes")).andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ---- Sin N+1 ----

    private long statements(int count) throws Exception {
        Room scenario = newRoom(10);
        Person viewer = person();
        for (int i = 0; i < count; i++) {
            Person org = person();
            Reservation r = reservation(org, scenario, BASE.plusDays(i), LocalTime.of(9, 0), LocalTime.of(10, 0));
            if (i % 2 == 0) {
                insertInvitation(r, viewer, i % 4 == 0 ? InvitationStatus.PENDING : InvitationStatus.ACCEPTED);
            }
        }
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // Calienta la sesión y la caché de usuario antes de medir
        list(viewer, "?salaId=" + scenario.getId() + "&size=" + count);
        stats.clear();
        JsonNode page = list(viewer, "?salaId=" + scenario.getId() + "&size=" + count);
        long executed = stats.getPrepareStatementCount();
        assertThat(page.get("content")).hasSize(count);
        long full = 0;
        for (JsonNode r : page.get("content")) {
            if (r.get("detalle").asString().equals("COMPLETO")) {
                full++;
            }
        }
        assertThat(full).isEqualTo((count + 1) / 2);
        return executed;
    }

    @Test
    void elListadoConPrivacidadNoHaceUnaConsultaPorReserva() throws Exception {
        long con4 = statements(4);
        long con24 = statements(24);
        assertThat(con24).isLessThanOrEqualTo(con4);
    }

    @Test
    void elListadoMisNoHaceUnaConsultaPorReserva() throws Exception {
        long[] counts = new long[2];
        int[] sizes = {4, 24};
        for (int k = 0; k < 2; k++) {
            Room scenario = newRoom(10);
            Person viewer = person();
            for (int i = 0; i < sizes[k]; i++) {
                Person org = person();
                Reservation r = reservation(org, scenario, BASE.plusDays(i), LocalTime.of(9, 0),
                        LocalTime.of(10, 0));
                insertInvitation(r, viewer, InvitationStatus.PENDING);
            }
            list(viewer, "?mis=true&salaId=" + scenario.getId() + "&size=" + sizes[k]);
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();
            JsonNode page = list(viewer, "?mis=true&salaId=" + scenario.getId() + "&size=" + sizes[k]);
            counts[k] = stats.getPrepareStatementCount();
            assertThat(page.get("content")).hasSize(sizes[k]);
        }
        assertThat(counts[1]).isLessThanOrEqualTo(counts[0]);
    }

    @Test
    void pendientesNoHaceUnaConsultaPorInvitacion() throws Exception {
        long[] counts = new long[2];
        int[] sizes = {3, 15};
        for (int k = 0; k < 2; k++) {
            Person viewer = person();
            for (int i = 0; i < sizes[k]; i++) {
                Reservation r = reservation(person(), room, BASE.plusDays(i), LocalTime.of(9, 0),
                        LocalTime.of(10, 0));
                insertInvitation(r, viewer, InvitationStatus.PENDING);
            }
            viewer.mvc().perform(get("/api/invitaciones/pendientes")).andExpect(status().isOk());
            Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            stats.clear();
            viewer.mvc().perform(get("/api/invitaciones/pendientes"))
                    .andExpect(jsonPath("$", hasSize(sizes[k])));
            counts[k] = stats.getPrepareStatementCount();
        }
        assertThat(counts[1]).isLessThanOrEqualTo(counts[0]);
    }
}
