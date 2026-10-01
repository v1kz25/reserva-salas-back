package com.reservasalas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reservasalas.entity.Room;
import com.reservasalas.repository.RoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
 * Flujo completo de alta de reservas con contexto real, H2 propia y reloj fijo.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flujo-${random.uuid}")
@WebAppConfiguration
@Import(ReservaFlujoIntegrationTest.FixedClockConfig.class)
class ReservaFlujoIntegrationTest {

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

    private MockMvc mvc;
    private Long roomId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        roomId = rooms.save(new Room("Sala flujo " + java.util.UUID.randomUUID(), 8, 1)).getId();
    }

    private ResultActions crear(String start, String end) throws Exception {
        String body = """
                {"salaId":%d,"fecha":"2030-05-10","horaInicio":"%s","horaFin":"%s",
                 "responsable":"Ana","motivo":"Reunión"}""".formatted(roomId, start, end);
        return mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void crearSolapadaContiguaEInvalida() throws Exception {
        crear("10:00", "11:00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horaInicio").value("10:00"));

        crear("10:00", "11:00")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));

        crear("11:00", "12:00").andExpect(status().isCreated());

        crear("13:00", "13:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("horaFin"));
    }

    private ResultActions crearEnFecha(String date) throws Exception {
        String body = """
                {"salaId":%d,"fecha":"%s","horaInicio":"10:00","horaFin":"11:00",
                 "responsable":"Ana","motivo":"Reunión"}""".formatted(roomId, date);
        return mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void listarConRangoInvertidoDa400ConCampoFechaHasta() throws Exception {
        mvc.perform(get("/api/reservas").param("fechaDesde", "2030-05-12").param("fechaHasta", "2030-05-10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores[0].campo").value("fechaHasta"));
    }

    @Test
    void reservarHoyDa400YMananaEsValido() throws Exception {
        // Reloj fijo: hoy es 2026-10-01 en Europe/Madrid
        crearEnFecha("2026-10-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"));
        crearEnFecha("2026-09-30")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"));
        crearEnFecha("2026-10-02").andExpect(status().isCreated());
    }

    @Test
    void listarFiltraPorRangoInclusivoYSala() throws Exception {
        crearEnFecha("2026-10-02").andExpect(status().isCreated());
        crearEnFecha("2026-10-03").andExpect(status().isCreated());

        mvc.perform(get("/api/reservas").param("salaId", roomId.toString())
                        .param("fechaDesde", "2026-10-03").param("fechaHasta", "2026-10-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fecha").value("2026-10-03"));
        mvc.perform(get("/api/reservas").param("salaId", roomId.toString()).param("fechaHasta", "2026-10-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fecha").value("2026-10-02"));
        mvc.perform(get("/api/reservas").param("salaId", roomId.toString()).param("fechaDesde", "2026-10-02"))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
