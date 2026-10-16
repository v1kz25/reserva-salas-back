package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reservasalas.config.CurrentTimeZone;
import com.reservasalas.entity.AppSetting;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.Room;
import com.reservasalas.entity.User;
import com.reservasalas.repository.AppSettingRepository;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.RoomRepository;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.support.TestUsers;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Zona horaria configurable: permisos, validación, persistencia y efecto en caliente, con la
 * cadena de seguridad real, H2 propia y reloj fijo en 2026-10-15T23:30Z (en Madrid ya es día 16;
 * en Nueva York aún es día 15).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:zona-${random.uuid}")
@WebAppConfiguration
@Import(ZonaHorariaIntegrationTest.FixedClockConfig.class)
class ZonaHorariaIntegrationTest {

    private static final String URL = "/api/config/zona-horaria";
    private static final LocalDate DAY_15 = LocalDate.of(2026, 10, 15);
    private static final LocalDate DAY_16 = LocalDate.of(2026, 10, 16);
    private static final LocalDate DAY_17 = LocalDate.of(2026, 10, 17);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-15T23:30:00Z"), ZoneId.of("Europe/Madrid"));
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private AppSettingRepository settings;
    @Autowired
    private CurrentTimeZone currentTimeZone;
    @Autowired
    private RoomRepository rooms;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private UserRepository users;

    private MockMvc admin;
    private MockMvc asUser;
    private MockMvc anonymous;
    private MockMvc adminNoCsrf;

    @BeforeEach
    void setUp() {
        admin = TestUsers.mockMvcAs(context, TestUsers.persisted(users, Role.ADMIN));
        asUser = TestUsers.mockMvcAs(context, TestUsers.persisted(users, Role.USER));
        anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminNoCsrf = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user(TestUsers.persisted(users, Role.ADMIN))))
                .build();
        currentTimeZone.change(ZoneId.of("Europe/Madrid"));
        settings.deleteAll();
    }

    @AfterEach
    void restore() {
        currentTimeZone.change(ZoneId.of("Europe/Madrid"));
    }

    private static String body(String zone) {
        return "{\"zonaHoraria\":\"%s\"}".formatted(zone);
    }

    private ResultActions enviar(MockMvc mvc, String json) throws Exception {
        return mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String stored() {
        return settings.findById(AppSetting.TIME_ZONE_KEY).map(AppSetting::getValue).orElse(null);
    }

    // ---- Permisos ----

    @Test
    void getDevuelve200ParaUserYAdminConLaZonaVigente() throws Exception {
        for (MockMvc mvc : List.of(asUser, admin)) {
            mvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.zonaHoraria").value("Europe/Madrid"));
        }
    }

    @Test
    void sinSesionGetYPutDan401() throws Exception {
        anonymous.perform(get(URL)).andExpect(status().isUnauthorized());
        anonymous.perform(put(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("UTC")))
                .andExpect(status().isUnauthorized());
        assertThat(currentTimeZone.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
    }

    @Test
    void putDeUserDa403YNoCambiaNada() throws Exception {
        enviar(asUser, body("UTC")).andExpect(status().isForbidden());
        assertThat(currentTimeZone.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
        assertThat(stored()).isNull();
    }

    @Test
    void putDeUserConCuerpoInvalidoDa403AntesQue400() throws Exception {
        enviar(asUser, body("Invalid/City")).andExpect(status().isForbidden());
    }

    @Test
    void putSinCsrfDa403() throws Exception {
        enviar(adminNoCsrf, body("UTC")).andExpect(status().isForbidden());
        assertThat(currentTimeZone.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
        assertThat(stored()).isNull();
    }

    // ---- PUT ----

    @Test
    void putValidoDa200DevuelveLaZonaYLaAplicaYPersiste() throws Exception {
        enviar(admin, body("America/New_York"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.zonaHoraria").value("America/New_York"));

        admin.perform(get(URL)).andExpect(jsonPath("$.zonaHoraria").value("America/New_York"));
        assertThat(currentTimeZone.zone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(stored()).isEqualTo("America/New_York");
    }

    @Test
    void putConEspaciosSeGuardaRecortado() throws Exception {
        enviar(admin, body("  UTC "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("UTC"));
        assertThat(stored()).isEqualTo("UTC");
        assertThat(currentTimeZone.zone().getId()).isEqualTo("UTC");
    }

    @Test
    void putConRegionDeTresNivelesDa200() throws Exception {
        enviar(admin, body("America/Argentina/Buenos_Aires"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("America/Argentina/Buenos_Aires"));
    }

    @Test
    void putIgnoraCamposDesconocidos() throws Exception {
        enviar(admin, "{\"zonaHoraria\":\"Asia/Tokyo\",\"extra\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonaHoraria").value("Asia/Tokyo"));
    }

    @Test
    void putInvalidoDa400ConErrorEnZonaHorariaYNoCambiaNada() throws Exception {
        List<String> bodies = List.of(
                body("Invalid/City"),
                body("+02:00"),
                body(""),
                body("   "),
                "{\"zonaHoraria\":null}",
                "{}",
                body("europe/madrid"),
                body("EUROPE/MADRID"),
                body("Etc/GMT+1"),
                body("Etc/GMT-14"),
                body("SystemV/AST4"),
                body("GMT0"),
                body("EST5EDT"));
        for (String json : bodies) {
            enviar(admin, json)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errores[0].campo").value("zonaHoraria"));
        }
        assertThat(currentTimeZone.zone()).isEqualTo(ZoneId.of("Europe/Madrid"));
        assertThat(stored()).isNull();
    }

    @Test
    void putConZonaInvalidaTrasUnaValidaConservaLaValida() throws Exception {
        enviar(admin, body("UTC")).andExpect(status().isOk());
        enviar(admin, body("Invalid/City")).andExpect(status().isBadRequest());
        assertThat(currentTimeZone.zone().getId()).isEqualTo("UTC");
        assertThat(stored()).isEqualTo("UTC");
    }

    @Test
    void putSinCuerpoOConJsonRotoDa400() throws Exception {
        admin.perform(put(URL).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        enviar(admin, "{no es json").andExpect(status().isBadRequest());
    }

    // ---- En caliente ----

    private Room newRoom() {
        return rooms.save(new Room("Sala zona " + UUID.randomUUID(), 6, 1));
    }

    private ResultActions reservar(Room room, LocalDate date) throws Exception {
        User owner = users.save(new User("z-" + UUID.randomUUID() + "@example.com", "Ana", "hash", Role.USER, java.time.Instant.parse("2026-10-01T00:00:00Z")));
        MockMvc mvc = TestUsers.mockMvcAs(context, AuthenticatedUser.from(owner));
        String json = """
                {"salaId":%d,"fecha":"%s","horaInicio":"10:00","horaFin":"11:00","motivo":"Reunión"}"""
                .formatted(room.getId(), date);
        return mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void conMadridReservarElDia16Da400YElDia17Da201() throws Exception {
        Room room = newRoom();
        reservar(room, DAY_16)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"));
        reservar(room, DAY_17).andExpect(status().isCreated());
    }

    @Test
    void trasCambiarANuevaYorkReservarElDia16Da201() throws Exception {
        Room room = newRoom();
        reservar(room, DAY_16).andExpect(status().isBadRequest());

        enviar(admin, body("America/New_York")).andExpect(status().isOk());

        reservar(room, DAY_16).andExpect(status().isCreated());
        // Y ahora el día 15 es «hoy» y sigue sin poder reservarse.
        reservar(room, DAY_15).andExpect(status().isBadRequest());
    }

    @Test
    void trasVolverAMadridElDia16VuelveAEstarProhibido() throws Exception {
        Room room = newRoom();
        enviar(admin, body("America/New_York")).andExpect(status().isOk());
        enviar(admin, body("Europe/Madrid")).andExpect(status().isOk());
        reservar(room, DAY_16).andExpect(status().isBadRequest());
    }

    private void reservaDirecta(Room room, LocalDate date) {
        User owner = users.save(new User("d-" + UUID.randomUUID() + "@example.com", "Ana", "hash", Role.USER, java.time.Instant.parse("2026-10-01T00:00:00Z")));
        reservations.save(new Reservation(room, date, LocalTime.of(10, 0), LocalTime.of(11, 0), owner, "Motivo"));
    }

    @Test
    void eliminarSalaConReservaDelDia15EsPasadaEnMadridYHoyEnNuevaYork() throws Exception {
        Room room = newRoom();
        reservaDirecta(room, DAY_15);

        // Nueva York: el día 15 es hoy, así que cuenta como futura.
        enviar(admin, body("America/New_York")).andExpect(status().isOk());
        admin.perform(delete("/api/salas/" + room.getId())).andExpect(status().isConflict());
        assertThat(rooms.existsById(room.getId())).isTrue();

        // Madrid: el día 15 ya es ayer.
        enviar(admin, body("Europe/Madrid")).andExpect(status().isOk());
        reservations.findAll().stream()
                .filter(r -> r.getRoom().getId().equals(room.getId()))
                .forEach(r -> assertThat(r.getDate()).isEqualTo(DAY_15));
        admin.perform(delete("/api/salas/" + room.getId())).andExpect(status().isNoContent());
    }

    // ---- Concurrencia ----

    @Test
    void dosPutSimultaneosDejanEnMemoriaLaMismaZonaQueEnLaBaseDeDatos() throws Exception {
        String[] zones = {"Asia/Tokyo", "America/New_York"};
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 40; round++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = List.of(
                        pool.submit(() -> concurrentPut(start, zones[0])),
                        pool.submit(() -> concurrentPut(start, zones[1])));
                start.countDown();
                for (Future<Integer> f : results) {
                    assertThat(f.get()).isEqualTo(200);
                }
                assertThat(currentTimeZone.zone().getId()).isEqualTo(stored());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private int concurrentPut(CountDownLatch start, String zone) throws Exception {
        start.await();
        return enviar(admin, body(zone)).andReturn().getResponse().getStatus();
    }
}
