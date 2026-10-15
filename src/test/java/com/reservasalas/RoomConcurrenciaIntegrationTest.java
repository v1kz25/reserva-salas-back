package com.reservasalas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/** Restricción única y concurrencia con H2 real. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:salas-conc-${random.uuid}")
@WebAppConfiguration
@Import(RoomConcurrenciaIntegrationTest.FixedClockConfig.class)
class RoomConcurrenciaIntegrationTest {

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
    @MockitoSpyBean
    private RoomRepository rooms;
    @Autowired
    private ReservationRepository reservations;
    @Autowired
    private UserRepository users;

    private MockMvc admin;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        admin = TestUsers.mockMvcAs(context, TestUsers.ADMIN);
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        reset(rooms);
    }

    private static String body(String name) {
        return "{\"nombre\":\"%s\",\"capacidad\":4,\"planta\":1}".formatted(name);
    }

    @Test
    void violacionDeLaRestriccionUnicaSaltandoLaComprobacionPreviaDa409NoDa500() throws Exception {
        String name = "Sala única " + UUID.randomUUID();
        rooms.save(new Room(name, 4, 1));
        doReturn(false).when(rooms).existsByNameKey(anyString());
        doReturn(false).when(rooms).existsByNameKeyAndIdNot(anyString(), anyLong());

        admin.perform(post("/api/salas").contentType(MediaType.APPLICATION_JSON).content(body(name.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Nombre de sala repetido"));

        Room other = rooms.save(new Room("Otra " + UUID.randomUUID(), 4, 1));
        admin.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/salas/" + other.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(body(" " + name + " ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Nombre de sala repetido"));
        assertThat(rooms.findAll().stream().filter(r -> r.getName().equalsIgnoreCase(name))).hasSize(1);
    }

    @Test
    void dosAltasSimultaneasConElMismoNombreDan201Y409() throws Exception {
        for (int i = 0; i < 5; i++) {
            String name = "Concurrente " + UUID.randomUUID();
            CountDownLatch start = new CountDownLatch(1);
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                String variant = t == 0 ? name : " " + name.toUpperCase() + " ";
                tasks.add(() -> {
                    start.await();
                    return admin.perform(post("/api/salas").contentType(MediaType.APPLICATION_JSON)
                            .content(body(variant))).andReturn().getResponse().getStatus();
                });
            }
            List<Future<Integer>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(pool.submit(task)));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(rooms.findAll().stream().filter(r -> r.getName().equalsIgnoreCase(name.strip()))).hasSize(1);
        }
    }

    @Test
    void borradoDeSalaYAltaDeReservaSimultaneosNuncaDejanReservaHuerfana() throws Exception {
        User owner = users.save(new User("c-" + UUID.randomUUID() + "@example.com", "Ana", "hash", Role.USER));
        MockMvc ana = TestUsers.mockMvcAs(context, com.reservasalas.security.AuthenticatedUser.from(owner));
        for (int i = 0; i < 10; i++) {
            Room room = rooms.save(new Room("Carrera " + UUID.randomUUID(), 4, 1));
            Long id = room.getId();
            String reservation = """
                    {"salaId":%d,"fecha":"2030-05-10","horaInicio":"10:00","horaFin":"11:00","motivo":"x"}"""
                    .formatted(id);
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> create = pool.submit(() -> {
                start.await();
                return ana.perform(post("/api/reservas").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservation)).andReturn().getResponse().getStatus();
            });
            Future<Integer> remove = pool.submit(() -> {
                start.await();
                return admin.perform(delete("/api/salas/" + id)).andReturn().getResponse().getStatus();
            });
            start.countDown();
            int created = create.get(30, TimeUnit.SECONDS);
            int removed = remove.get(30, TimeUnit.SECONDS);

            boolean roomExists = rooms.existsById(id);
            boolean hasReservation = reservations.existsByRoomFrom(id, LocalDate.of(2000, 1, 1));
            if (created == 201) {
                assertThat(removed).as("iteración %d", i).isEqualTo(409);
                assertThat(roomExists).isTrue();
                assertThat(hasReservation).isTrue();
            } else {
                assertThat(created).as("iteración %d", i).isEqualTo(404);
                assertThat(removed).isEqualTo(204);
                assertThat(roomExists).isFalse();
                assertThat(hasReservation).as("reserva huérfana").isFalse();
            }
        }
    }
}
