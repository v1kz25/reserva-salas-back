package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.Room;
import com.reservasalas.entity.User;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.RoomRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.support.TestUsers;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Gestión de salas con la cadena de seguridad real, H2 propia y reloj fijo (hoy = 2026-10-01).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:salas-${random.uuid}")
@WebAppConfiguration
@Import(GestionSalasIntegrationTest.FixedClockConfig.class)
class GestionSalasIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneId.of("Europe/Madrid"));
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private RoomRepository rooms;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private UserRepository users;

    private MockMvc admin;
    private MockMvc asUser;
    private MockMvc anonymous;

    @BeforeEach
    void setUp() {
        admin = TestUsers.mockMvcAs(context, TestUsers.ADMIN);
        asUser = TestUsers.mockMvcAs(context, TestUsers.ANA);
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    private static String json(String name, Object capacity, Object floor) {
        return "{\"nombre\":\"%s\",\"capacidad\":%s,\"planta\":%s}".formatted(name, capacity, floor);
    }

    private ResultActions send(MockMvc mvc, MockHttpServletRequestBuilder builder, String body) throws Exception {
        return mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Room newRoom(String name) {
        return rooms.save(new Room(name, 6, 1));
    }

    private void reserve(Room room, LocalDate date) {
        User owner = users.save(new User("u-" + UUID.randomUUID() + "@example.com", "Ana", "hash", Role.USER));
        reservations.save(new Reservation(room, date, LocalTime.of(10, 0), LocalTime.of(11, 0), owner, "Motivo"));
    }

    // ---- Permisos ----

    @Test
    void sinSesionTodosLosEndpointsDan401() throws Exception {
        Long id = newRoom(unique("Sala")).getId();
        String body = json("X", 3, 1);

        anonymous.perform(get("/api/salas")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/api/salas/" + id)).andExpect(status().isUnauthorized());
        send(anonymous, post("/api/salas").with(csrf()), body).andExpect(status().isUnauthorized());
        send(anonymous, put("/api/salas/" + id).with(csrf()), body).andExpect(status().isUnauthorized());
        anonymous.perform(delete("/api/salas/" + id).with(csrf())).andExpect(status().isUnauthorized());
        assertThat(rooms.existsById(id)).isTrue();
    }

    @Test
    void userPuedeLeerPeroNoModificarSalas() throws Exception {
        Room room = newRoom(unique("Sala"));
        String body = json(unique("Otra"), 3, 1);

        asUser.perform(get("/api/salas")).andExpect(status().isOk());
        asUser.perform(get("/api/salas/" + room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(room.getName()));
        send(asUser, post("/api/salas"), body).andExpect(status().isForbidden());
        send(asUser, put("/api/salas/" + room.getId()), body).andExpect(status().isForbidden());
        asUser.perform(delete("/api/salas/" + room.getId())).andExpect(status().isForbidden());

        assertThat(rooms.findById(room.getId())).get().extracting(Room::getName).isEqualTo(room.getName());
    }

    @Test
    void userNoPuedeBorrarSalaInexistenteNiCrearConDatosInvalidos() throws Exception {
        // La autorización va antes que la validación y que el 404.
        asUser.perform(delete("/api/salas/999999")).andExpect(status().isForbidden());
        send(asUser, post("/api/salas"), json("", 0, 1)).andExpect(status().isForbidden());
    }

    @Test
    void adminPuedeLeerListadoYSala() throws Exception {
        Room room = newRoom(unique("Sala"));
        admin.perform(get("/api/salas")).andExpect(status().isOk());
        admin.perform(get("/api/salas/" + room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(room.getId()))
                .andExpect(jsonPath("$.capacidad").value(6))
                .andExpect(jsonPath("$.planta").value(1));
    }

    // ---- Alta ----

    @Test
    void adminCreaSalaConLocationYNombreRecortado() throws Exception {
        String name = unique("Sala nueva");
        ResultActions result = send(admin, post("/api/salas"), json("   " + name + "  ", 12, 3))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/salas/")))
                .andExpect(jsonPath("$.nombre").value(name))
                .andExpect(jsonPath("$.capacidad").value(12))
                .andExpect(jsonPath("$.planta").value(3));
        String location = result.andReturn().getResponse().getHeader("Location");
        Long id = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));

        result.andExpect(header().string("Location", endsWith("/api/salas/" + id)));
        admin.perform(get("/api/salas/" + id)).andExpect(jsonPath("$.nombre").value(name));
        assertThat(rooms.findById(id).orElseThrow().getName()).isEqualTo(name);
    }

    @Test
    void crearIgnoraIdYCamposDesconocidos() throws Exception {
        String name = unique("Sala id");
        String body = "{\"id\":987654,\"nombre\":\"%s\",\"capacidad\":4,\"planta\":0,\"extra\":true}".formatted(name);
        send(admin, post("/api/salas"), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(987654)));
    }

    @Test
    void plantaNegativaOCeroEsValida() throws Exception {
        send(admin, post("/api/salas"), json(unique("Sótano"), 5, -2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planta").value(-2));
        send(admin, post("/api/salas"), json(unique("Baja"), 5, 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planta").value(0));
    }

    @Test
    void nombreDeCienCaracteresEsValidoYCientoUnoNo() throws Exception {
        send(admin, post("/api/salas"), json("a".repeat(99) + "b", 5, 1)).andExpect(status().isCreated());
        send(admin, post("/api/salas"), json("c".repeat(101), 5, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
    }

    @Test
    void nombreDe100CaracteresMasEspaciosSeRecortaYEsValido() throws Exception {
        send(admin, post("/api/salas"), json("  " + "d".repeat(100) + "  ", 5, 1)).andExpect(status().isCreated());
    }

    @Test
    void nombreVacioOSoloEspaciosDa400() throws Exception {
        for (String name : new String[] {"", "    "}) {
            send(admin, post("/api/salas"), json(name, 5, 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        }
    }

    @Test
    void capacidadCeroONegativaDa400() throws Exception {
        for (int capacity : new int[] {0, -1}) {
            send(admin, post("/api/salas"), json(unique("Sala"), capacity, 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("capacidad"));
        }
    }

    @Test
    void capacidadYPlantaAusentesDan400ConUnErrorPorCampo() throws Exception {
        send(admin, post("/api/salas"), "{\"nombre\":\"" + unique("Sala") + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[?(@.campo=='capacidad')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='planta')]").exists());
    }

    @Test
    void plantaNulaDa400() throws Exception {
        send(admin, post("/api/salas"), json(unique("Sala"), 5, "null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("planta"));
    }

    @Test
    void cuerpoNoJsonOAusenteDa400SinDatosInternos() throws Exception {
        send(admin, post("/api/salas"), "{no es json")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("com.reservasalas"))));
    }

    // ---- Nombre duplicado ----

    @Test
    void crearConNombreDuplicadoDa409AunCambiandoMayusculasYEspacios() throws Exception {
        String name = unique("Sala Única");
        send(admin, post("/api/salas"), json(name, 5, 1)).andExpect(status().isCreated());

        for (String variant : new String[] {name, name.toUpperCase(), "  " + name.toLowerCase() + "   "}) {
            send(admin, post("/api/salas"), json(variant, 5, 1))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.title").value("Nombre de sala repetido"))
                    .andExpect(content().string(not(containsString(name))))
                    .andExpect(content().string(not(containsString(name.toUpperCase()))));
        }
    }

    @Test
    void modificarConNombreDeOtraSalaDa409() throws Exception {
        String other = unique("Ocupada");
        newRoom(other);
        Room mine = newRoom(unique("Mia"));

        for (String variant : new String[] {other, " " + other.toUpperCase() + " "}) {
            send(admin, put("/api/salas/" + mine.getId()), json(variant, 5, 1))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Nombre de sala repetido"))
                    .andExpect(content().string(not(containsString(other))));
        }
        assertThat(rooms.findById(mine.getId()).orElseThrow().getName()).startsWith("Mia");
    }

    @Test
    void modificarMantieneElPropioNombreAunqueCambieMayusculasYEspacios() throws Exception {
        String name = unique("Propia");
        Room room = newRoom(name);

        send(admin, put("/api/salas/" + room.getId()), json(name, 20, 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidad").value(20))
                .andExpect(jsonPath("$.planta").value(4));
        send(admin, put("/api/salas/" + room.getId()), json("  " + name.toUpperCase() + " ", 21, 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(name.toUpperCase()));
    }

    @Test
    void modificarGuardaNombreRecortadoYDevuelveLaSala() throws Exception {
        Room room = newRoom(unique("Vieja"));
        String name = unique("Nueva");
        send(admin, put("/api/salas/" + room.getId()), json("  " + name + "  ", 9, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(room.getId()))
                .andExpect(jsonPath("$.nombre").value(name));
        assertThat(rooms.findById(room.getId()).orElseThrow().getName()).isEqualTo(name);
        // El nombre anterior queda libre.
        send(admin, post("/api/salas"), json(room.getName(), 3, 1)).andExpect(status().isCreated());
    }

    @Test
    void modificarConDatosInvalidosDa400() throws Exception {
        Room room = newRoom(unique("Sala"));
        send(admin, put("/api/salas/" + room.getId()), json("  ", 0, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2));
        send(admin, put("/api/salas/" + room.getId()), "{\"nombre\":\"x\"}")
                .andExpect(status().isBadRequest());
    }

    // ---- 404 ----

    @Test
    void salaInexistenteDa404EnGetPutYDelete() throws Exception {
        admin.perform(get("/api/salas/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        send(admin, put("/api/salas/999999"), json(unique("Sala"), 5, 1)).andExpect(status().isNotFound());
        admin.perform(delete("/api/salas/999999")).andExpect(status().isNotFound());
    }

    @Test
    void idNoNumericoDa400() throws Exception {
        admin.perform(get("/api/salas/abc")).andExpect(status().isBadRequest());
    }

    // ---- Borrado ----

    @Test
    void borrarSalaSinReservasDa204() throws Exception {
        Room room = newRoom(unique("Vacía"));
        admin.perform(delete("/api/salas/" + room.getId())).andExpect(status().isNoContent());
        assertThat(rooms.existsById(room.getId())).isFalse();
        admin.perform(get("/api/salas/" + room.getId())).andExpect(status().isNotFound());
    }

    @Test
    void borrarSalaConReservaDeHoyDa409() throws Exception {
        Room room = newRoom(unique("Hoy"));
        reserve(room, TODAY);

        admin.perform(delete("/api/salas/" + room.getId()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sala con reservas futuras"));
        assertThat(rooms.existsById(room.getId())).isTrue();
        assertThat(reservations.existsByRoomFrom(room.getId(), TODAY)).isTrue();
    }

    @Test
    void borrarSalaConReservaFuturaDa409() throws Exception {
        Room room = newRoom(unique("Futura"));
        reserve(room, TODAY.plusDays(30));

        admin.perform(delete("/api/salas/" + room.getId())).andExpect(status().isConflict());
        assertThat(rooms.existsById(room.getId())).isTrue();
        assertThat(reservations.existsByRoomFrom(room.getId(), TODAY)).isTrue();
    }

    @Test
    void borrarSalaConReservaPasadaYFuturaDa409YNoBorraNada() throws Exception {
        Room room = newRoom(unique("Mixta"));
        reserve(room, TODAY.minusDays(3));
        reserve(room, TODAY.plusDays(1));

        admin.perform(delete("/api/salas/" + room.getId())).andExpect(status().isConflict());
        assertThat(rooms.existsById(room.getId())).isTrue();
        assertThat(reservations.existsByRoomFrom(room.getId(), TODAY.minusDays(10))).isTrue();
    }

    @Test
    void borrarSalaConSoloReservasPasadasDa204YBorraTambienLasReservas() throws Exception {
        Room room = newRoom(unique("Pasada"));
        Room other = newRoom(unique("Otra"));
        reserve(room, TODAY.minusDays(1));
        reserve(room, TODAY.minusDays(40));
        reserve(other, TODAY.minusDays(1));

        admin.perform(delete("/api/salas/" + room.getId())).andExpect(status().isNoContent());

        assertThat(rooms.existsById(room.getId())).isFalse();
        assertThat(reservations.existsByRoomFrom(room.getId(), TODAY.minusYears(10))).isFalse();
        // Las reservas de otras salas no se tocan.
        assertThat(reservations.existsByRoomFrom(other.getId(), TODAY.minusYears(10))).isTrue();
    }

    @Test
    void borrarDosVecesDa204Y404() throws Exception {
        Room room = newRoom(unique("Doble"));
        admin.perform(delete("/api/salas/" + room.getId())).andExpect(status().isNoContent());
        admin.perform(delete("/api/salas/" + room.getId())).andExpect(status().isNotFound());
    }

    // ---- CSRF ----

    @Test
    void adminSinTokenCsrfDa403() throws Exception {
        MockMvc withoutToken = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Room room = newRoom(unique("Csrf"));
        withoutToken.perform(delete("/api/salas/" + room.getId()).with(user(TestUsers.ADMIN)))
                .andExpect(status().isForbidden());
        assertThat(rooms.existsById(room.getId())).isTrue();
    }

    @Test
    void espaciosInternosDuplicadosNoEvitanElConflictoYSeGuardaUnSoloEspacio() throws Exception {
        String suffix = UUID.randomUUID().toString();
        send(admin, post("/api/salas"), json("Sala Nueva " + suffix, 5, 1))
                .andExpect(status().isCreated());

        send(admin, post("/api/salas"), json("  SALA   NUEVA   " + suffix.toUpperCase() + " ", 5, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Nombre de sala repetido"));

        String spaced = "Sala   Doble\t\u00a0Espacio " + suffix;
        String body = "{\"nombre\":\"Sala   Doble\\t\\u00a0Espacio " + suffix + "\",\"capacidad\":2,\"planta\":1}";
        send(admin, post("/api/salas"), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Sala Doble Espacio " + suffix));
        assertThat(rooms.findAll()).extracting(Room::getName).noneMatch(n -> n.contains("  "));
        assertThat(spaced).contains("   ");
    }
}
