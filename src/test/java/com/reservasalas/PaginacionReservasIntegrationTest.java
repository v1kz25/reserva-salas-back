package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.reservasalas.entity.Reservation;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.Room;
import com.reservasalas.entity.User;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * Paginación de {@code GET /api/reservas} con contexto real, H2 propia y reloj fijo: tamaños y
 * páginas, validación, filtros, orden estable, forma del JSON y ausencia de consultas N+1.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paginacion-${random.uuid}",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@WebAppConfiguration
@Import(PaginacionReservasIntegrationTest.FixedClockConfig.class)
class PaginacionReservasIntegrationTest {

    private static final String URL = "/api/reservas";
    private static final LocalDate BASE = LocalDate.of(2030, 5, 1);

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
    private UserRepository users;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;
    private Room roomA;
    private Room roomB;
    private Room roomC;
    private List<User> people;
    private boolean viewerIsAdmin;

    @BeforeEach
    void setUp() {
        reservations.deleteAll();
        people = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            people.add(users.save(new User("pag-" + UUID.randomUUID() + "@example.com", "Persona " + i,
                    "$2a$10$abcdefghijklmnopqrstuuJ1lRkXH8aY0Y8cU5qOjK8m7cOQ1gYfS", Role.USER, Instant.EPOCH)));
        }
        mvc = TestUsers.mockMvcAs(context, AuthenticatedUser.from(people.get(0)));
        roomA = rooms.save(new Room("Pag A " + UUID.randomUUID(), 8, 1));
        roomB = rooms.save(new Room("Pag B " + UUID.randomUUID(), 8, 1));
        roomC = rooms.save(new Room("Pag C " + UUID.randomUUID(), 8, 1));
    }

    /** Crea n reservas repartidas por salas, días y usuarios, sin solapes en una misma sala. */
    private void seed(int n) {
        Room[] all = {roomA, roomB, roomC};
        for (int i = 0; i < n; i++) {
            Room room = all[i % 3];
            LocalDate day = BASE.plusDays(i / 3);
            reservations.save(new Reservation(room, day, LocalTime.of(9, 0), LocalTime.of(10, 0),
                    people.get(i % people.size()), "Motivo " + i));
        }
    }

    private JsonNode json(String query) throws Exception {
        String body = mvc.perform(get(URL + query)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return mapper.readTree(body);
    }

    // ---- Valores por defecto y metadatos ----

    @Test
    void sinParametrosDevuelvePrimerosVeinteConMetadatos() throws Exception {
        seed(47);
        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.totalElements").value(47))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void paginaUnoEmpiezaDondeTerminaLaPaginaCeroSinSolaparse() throws Exception {
        seed(47);
        JsonNode first = json("");
        JsonNode second = json("?page=1");
        assertThat(second.get("number").asInt()).isEqualTo(1);
        assertThat(second.get("content")).hasSize(20);
        assertThat(second.get("content").get(0).get("id").asLong())
                .isNotEqualTo(first.get("content").get(0).get("id").asLong());
        assertThat(second.get("content").get(0).get("id").asLong())
                .isGreaterThan(first.get("content").get(19).get("id").asLong());
    }

    @Test
    void ultimaPaginaParcialTieneSieteElementos() throws Exception {
        seed(47);
        mvc.perform(get(URL).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(7)))
                .andExpect(jsonPath("$.totalElements").value(47))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(2));
    }

    @Test
    void paginaFueraDeRangoEsVaciaConTotalesCorrectosYNumberPedido() throws Exception {
        seed(47);
        mvc.perform(get(URL).param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(47))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.number").value(99))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void sinReservasDevuelvePaginaVacia() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void sizeCienEsValidoYSizeUnoTambien() throws Exception {
        seed(47);
        mvc.perform(get(URL).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(47)))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get(URL).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(47));
    }

    // ---- Validación ----

    @ParameterizedTest
    @CsvSource({"size,101", "size,0", "size,-5", "page,-1", "size,abc", "page,abc", "page,1.5"})
    void parametrosInvalidosDan400ConErroresEnEspanol(String name, String value) throws Exception {
        seed(3);
        mvc.perform(get(URL).param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value(name))
                .andExpect(jsonPath("$.errores[0].mensaje").isNotEmpty());
    }

    @Test
    void mensajesDeLimitesEstanEnEspanol() throws Exception {
        mvc.perform(get(URL).param("size", "101"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("no puede ser mayor que 100"));
        mvc.perform(get(URL).param("size", "0"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("debe ser al menos 1"));
        mvc.perform(get(URL).param("page", "-1"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("debe ser al menos 0"));
    }

    @Test
    void parametrosInvalidosDeVariosCamposSeReportanJuntos() throws Exception {
        mvc.perform(get(URL).param("page", "-1").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores", hasSize(2)))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem("page")))
                .andExpect(jsonPath("$.errores[*].campo").value(hasItem("size")));
    }

    // ---- Filtros ----

    @Test
    void filtroPorSalaCuentaSoloLasFiltradas() throws Exception {
        seed(47); // A: 16, B: 16, C: 15
        mvc.perform(get(URL).param("salaId", roomA.getId().toString()).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.totalElements").value(16))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].salaId").value(not(hasItem(roomB.getId().intValue()))));
        mvc.perform(get(URL).param("salaId", roomC.getId().toString()).param("size", "10").param("page", "1"))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.totalElements").value(15));
    }

    @Test
    void filtroPorFechasCuentaSoloLasFiltradas() throws Exception {
        seed(47); // 16 días: día 0..15 (3 por día salvo el último, con 2)
        // Días 2..4 -> 9 reservas
        mvc.perform(get(URL).param("fechaDesde", BASE.plusDays(2).toString())
                        .param("fechaHasta", BASE.plusDays(4).toString()).param("size", "4"))
                .andExpect(jsonPath("$.totalElements").value(9))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content", hasSize(4)));
        // Solo fechaDesde: días 14 y 15 -> 3 + 2 = 5
        mvc.perform(get(URL).param("fechaDesde", BASE.plusDays(14).toString()))
                .andExpect(jsonPath("$.totalElements").value(5));
        // Solo fechaHasta: día 0 -> 3
        mvc.perform(get(URL).param("fechaHasta", BASE.toString()))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filtrosCombinadosConPaginacion() throws Exception {
        seed(47);
        // Sala B en los días 0..5 -> 6 reservas
        mvc.perform(get(URL).param("salaId", roomB.getId().toString())
                        .param("fechaDesde", BASE.toString()).param("fechaHasta", BASE.plusDays(5).toString())
                        .param("size", "4").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.number").value(1));
    }

    @Test
    void filtroSinResultadosDevuelvePaginaVacia() throws Exception {
        seed(10);
        mvc.perform(get(URL).param("fechaDesde", "2035-01-01"))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void rangoInvertidoSigueDando400ConPaginacion() throws Exception {
        mvc.perform(get(URL).param("fechaDesde", "2030-05-12").param("fechaHasta", "2030-05-10").param("size", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaHasta"));
    }

    // ---- Orden estable ----

    @Test
    void mismaFechaYHoraSeOrdenanPorIdYRecorrerPaginasNoRepiteNiPierde() throws Exception {
        // 30 reservas el mismo día a la misma hora, en tres salas distintas, insertadas en orden mezclado
        Room[] all = {roomC, roomA, roomB};
        for (int i = 0; i < 30; i++) {
            reservations.save(new Reservation(all[i % 3], BASE, LocalTime.of(9, 0), LocalTime.of(10, 0),
                    people.get(i % people.size()), "Mismo hueco " + i));
        }
        // Y otras con distinta hora para comprobar el orden primario
        reservations.save(new Reservation(roomA, BASE, LocalTime.of(8, 0), LocalTime.of(8, 30),
                people.get(0), "Temprana"));
        reservations.save(new Reservation(roomA, BASE.minusDays(1), LocalTime.of(18, 0), LocalTime.of(19, 0),
                people.get(0), "Día anterior"));

        List<Long> recorrido = new ArrayList<>();
        for (int page = 0; page < 7; page++) {
            JsonNode node = json("?size=5&page=" + page);
            for (JsonNode item : node.get("content")) {
                recorrido.add(item.get("id").asLong());
            }
        }
        assertThat(recorrido).hasSize(32).doesNotHaveDuplicates();
        assertThat(new HashSet<>(recorrido))
                .isEqualTo(new HashSet<>(reservations.findAll().stream().map(Reservation::getId).toList()));

        List<Long> esperado = reservations.findAll().stream()
                .sorted(Comparator.comparing(Reservation::getDate)
                        .thenComparing(Reservation::getStartTime)
                        .thenComparing(Reservation::getId))
                .map(Reservation::getId)
                .toList();
        assertThat(recorrido).containsExactlyElementsOf(esperado);
    }

    @Test
    void recorrerTodasLasPaginasSinFiltroNoRepiteNiPierdeNinguna() throws Exception {
        seed(47);
        Set<Long> vistos = new HashSet<>();
        int total = 0;
        for (int page = 0; page < 5; page++) {
            for (JsonNode item : json("?size=10&page=" + page).get("content")) {
                vistos.add(item.get("id").asLong());
                total++;
            }
        }
        assertThat(total).isEqualTo(47);
        assertThat(vistos).hasSize(47);
    }

    // ---- Forma del JSON ----

    @Test
    void elJsonTieneExactamenteLasClavesDelContrato() throws Exception {
        seed(3);
        JsonNode node = json("");
        List<String> keys = new ArrayList<>();
        node.propertyNames().forEach(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("content", "totalElements", "totalPages", "number", "size");
        assertThat(node.has("pageable")).isFalse();
        assertThat(node.has("sort")).isFalse();
        assertThat(node.has("first")).isFalse();
        assertThat(node.has("last")).isFalse();
        assertThat(node.has("empty")).isFalse();
        assertThat(node.has("numberOfElements")).isFalse();

        // La primera reserva es del usuario de la sesión: completa, sin invitaciones en el listado
        List<String> itemKeys = new ArrayList<>();
        node.get("content").get(0).propertyNames().forEach(itemKeys::add);
        assertThat(itemKeys).containsExactlyInAnyOrder(
                "id", "salaId", "fecha", "horaInicio", "horaFin", "usuarioId", "responsable", "motivo", "detalle");
        assertThat(node.get("content").get(0).get("detalle").asText()).isEqualTo("COMPLETO");

        // La segunda es de otro usuario: solo la franja ocupada
        List<String> busyKeys = new ArrayList<>();
        node.get("content").get(1).propertyNames().forEach(busyKeys::add);
        assertThat(busyKeys).containsExactlyInAnyOrder("id", "salaId", "fecha", "horaInicio", "horaFin", "detalle");
        assertThat(node.get("content").get(1).get("detalle").asText()).isEqualTo("OCUPADA");
    }

    // ---- N+1 ----

    @Test
    void paginaEnteroMaximoDevuelveVaciaConTotalesRealesYNunca500() throws Exception {
        seed(47);
        mvc.perform(get(URL).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(47))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(2147483647))
                .andExpect(jsonPath("$.size").value(100));
    }

    private long sentencias(int reservas) throws Exception {
        reservations.deleteAll();
        seed(reservas);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        // Página llena en ambos casos (size = reservas) para que Spring ejecute siempre el count
        JsonNode node = json("?size=" + reservas);
        assertThat(node.get("content")).hasSize(reservas);
        Set<Long> usuarios = new HashSet<>();
        node.get("content").forEach(r -> {
            if (r.has("usuarioId")) {
                usuarios.add(r.get("usuarioId").asLong());
            }
        });
        // Como USER solo ve completa la suya; como ADMIN, todas
        assertThat(usuarios).hasSize(viewerIsAdmin ? reservas : 1);
        return stats.getPrepareStatementCount();
    }

    @Test
    void elNumeroDeSentenciasNoCreceConElNumeroDeReservasNiDeUsuarios() throws Exception {
        long con5 = sentencias(5);
        long con20 = sentencias(20);
        assertThat(con20).isEqualTo(con5);
    }

    @Test
    void elNumeroDeSentenciasNoCreceConElNumeroDeReservasParaUnAdmin() throws Exception {
        User admin = users.save(new User("pag-" + UUID.randomUUID() + "@example.com", "Admin",
                "$2a$10$abcdefghijklmnopqrstuuJ1lRkXH8aY0Y8cU5qOjK8m7cOQ1gYfS", Role.ADMIN, Instant.EPOCH));
        mvc = TestUsers.mockMvcAs(context, AuthenticatedUser.from(admin));
        viewerIsAdmin = true;
        long con5 = sentencias(5);
        long con20 = sentencias(20);
        assertThat(con20).isEqualTo(con5);
    }
}
