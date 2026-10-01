package com.reservasalas.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reservasalas.dto.CreateReservationRequest;
import com.reservasalas.dto.ReservationDto;
import com.reservasalas.exception.InvalidDateRangeException;
import com.reservasalas.exception.InvalidReservationException;
import com.reservasalas.exception.OverlappingReservationException;
import com.reservasalas.exception.RoomNotFoundException;
import com.reservasalas.service.ReservationService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    private static final String VALID = """
            {"salaId":1,"fecha":"2030-05-10","horaInicio":"10:00","horaFin":"11:00",
             "responsable":"Ana","motivo":"Reunión"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReservationService service;

    private ResultActions enviar(String body) throws Exception {
        return mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String field, String value) {
        // Sustituye un campo del JSON válido
        return VALID.replaceAll("\"" + field + "\":(\"[^\"]*\"|[0-9]+)", "\"" + field + "\":" + value);
    }

    @Test
    void postCrea201ConLocationYHorasHHmm() throws Exception {
        when(service.create(any())).thenReturn(new ReservationDto(7L, 1L, LocalDate.of(2030, 5, 10),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "Ana", "Reunión"));

        enviar(VALID)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/reservas/7")))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.salaId").value(1))
                .andExpect(jsonPath("$.fecha").value("2030-05-10"))
                .andExpect(jsonPath("$.horaInicio").value("10:00"))
                .andExpect(jsonPath("$.horaFin").value("11:00"));

        verify(service).create(new CreateReservationRequest(1L, LocalDate.of(2030, 5, 10),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "Ana", "Reunión"));
    }

    @Test
    void solapamientoDevuelve409ProblemDetail() throws Exception {
        when(service.create(any())).thenThrow(new OverlappingReservationException(1L,
                LocalDate.of(2030, 5, 10), LocalTime.of(10, 0), LocalTime.of(11, 0)));

        enviar(VALID)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void salaInexistenteDevuelve404ProblemDetail() throws Exception {
        when(service.create(any())).thenThrow(new RoomNotFoundException(99L));

        enviar(VALID)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void reglaDeNegocioInvalidaDevuelve400ConErrores() throws Exception {
        when(service.create(any())).thenThrow(new InvalidReservationException("horaFin", "debe ser posterior a horaInicio"));

        enviar(body("horaFin", "\"10:00\""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores[0].campo").value("horaFin"))
                .andExpect(jsonPath("$.errores[0].mensaje").exists());
    }

    @Test
    void camposObligatoriosAusentesDan400ConUnErrorPorCampo() throws Exception {
        enviar("{}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores.length()").value(6))
                .andExpect(jsonPath("$.errores[?(@.campo=='salaId')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='fecha')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='horaInicio')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='horaFin')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='responsable')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='motivo')]").exists());
    }

    @Test
    void responsableYMotivoEnBlancoDan400() throws Exception {
        enviar(body("responsable", "\"   \"").replace("\"Reunión\"", "\"\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo=='responsable')]").exists())
                .andExpect(jsonPath("$.errores[?(@.campo=='motivo')]").exists());
    }

    @Test
    void responsableDe101CaracteresDa400() throws Exception {
        enviar(body("responsable", "\"" + "a".repeat(101) + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("responsable"));
    }

    @Test
    void responsableDe100CaracteresEsValido() throws Exception {
        when(service.create(any())).thenReturn(new ReservationDto(1L, 1L, LocalDate.of(2030, 5, 10),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "a".repeat(100), "Reunión"));
        enviar(body("responsable", "\"" + "a".repeat(100) + "\"")).andExpect(status().isCreated());
    }

    @Test
    void motivoDe256CaracteresDa400YDe255Es201() throws Exception {
        enviar(body("motivo", "\"" + "m".repeat(256) + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("motivo"));
        when(service.create(any())).thenReturn(new ReservationDto(1L, 1L, LocalDate.of(2030, 5, 10),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "Ana", "m".repeat(255)));
        enviar(body("motivo", "\"" + "m".repeat(255) + "\"")).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"10:00:00\"", "\"9:00\"", "\"10h\"", "\"25:00\"", "\"abc\""})
    void horaConFormatoNoHHmmDa400(String bad) throws Exception {
        enviar(body("horaInicio", bad))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores[0].campo").value("horaInicio"));
    }

    @Test
    void fechaNoValidaDa400() throws Exception {
        enviar(body("fecha", "\"10/05/2030\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"));
    }

    @Test
    void jsonMalformadoDa400() throws Exception {
        enviar("{no es json")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void getSinFiltrosDelegaConNulos() throws Exception {
        when(service.find(null, null, null)).thenReturn(List.of(new ReservationDto(1L, 1L,
                LocalDate.of(2030, 5, 10), LocalTime.of(9, 5), LocalTime.of(10, 0), "Ana", "x")));

        mvc.perform(get("/api/reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].horaInicio").value("09:05"));
    }

    @Test
    void getConFiltrosSalaIdYRangoDeFechas() throws Exception {
        LocalDate from = LocalDate.of(2030, 5, 10);
        LocalDate to = LocalDate.of(2030, 5, 12);
        when(service.find(3L, from, to)).thenReturn(List.of());

        mvc.perform(get("/api/reservas").param("salaId", "3")
                        .param("fechaDesde", "2030-05-10").param("fechaHasta", "2030-05-12"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verify(service).find(3L, from, to);
    }

    @Test
    void getConFechaInvalidaDa400ConErrores() throws Exception {
        mvc.perform(get("/api/reservas").param("fechaDesde", "ayer"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void getConSalaIdNoNumericoDa400() throws Exception {
        mvc.perform(get("/api/reservas").param("salaId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void getConRangoInvertidoDa400ConCampoFechaHasta() throws Exception {
        when(service.find(null, LocalDate.of(2030, 5, 12), LocalDate.of(2030, 5, 10)))
                .thenThrow(new InvalidDateRangeException("fechaHasta", "no puede ser anterior a fechaDesde"));

        mvc.perform(get("/api/reservas").param("fechaDesde", "2030-05-12").param("fechaHasta", "2030-05-10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores[0].campo").value("fechaHasta"))
                .andExpect(jsonPath("$.errores[0].mensaje").exists());
    }

    @Test
    void getConMismaFechaEnAmbasPuntasEsValido() throws Exception {
        LocalDate day = LocalDate.of(2030, 5, 10);
        when(service.find(null, day, day)).thenReturn(List.of());

        mvc.perform(get("/api/reservas").param("fechaDesde", "2030-05-10").param("fechaHasta", "2030-05-10"))
                .andExpect(status().isOk());
        verify(service).find(null, day, day);
    }

    @Test
    void getSoloConFechaDesdeDelegaConHastaNulo() throws Exception {
        LocalDate from = LocalDate.of(2030, 5, 10);
        when(service.find(null, from, null)).thenReturn(List.of());

        mvc.perform(get("/api/reservas").param("fechaDesde", "2030-05-10")).andExpect(status().isOk());
        verify(service).find(null, from, null);
    }

    @Test
    void getSoloConFechaHastaDelegaConDesdeNulo() throws Exception {
        LocalDate to = LocalDate.of(2030, 5, 10);
        when(service.find(null, null, to)).thenReturn(List.of());

        mvc.perform(get("/api/reservas").param("fechaHasta", "2030-05-10")).andExpect(status().isOk());
        verify(service).find(null, null, to);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ayer", "10/05/2030", "2030-13-01", "2030-02-30"})
    void getConFechaHastaInvalidaDa400ConCampoFechaHasta(String bad) throws Exception {
        mvc.perform(get("/api/reservas").param("fechaHasta", bad))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores[0].campo").value("fechaHasta"));
        verifyNoInteractions(service);
    }

    @Test
    void getConFechaDesdeInvalidaDa400ConCampoFechaDesde() throws Exception {
        mvc.perform(get("/api/reservas").param("fechaDesde", "ayer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fechaDesde"));
    }

    @Test
    void getCombinaSalaIdYRango() throws Exception {
        LocalDate from = LocalDate.of(2030, 5, 10);
        LocalDate to = LocalDate.of(2030, 5, 10);
        when(service.find(2L, from, to)).thenReturn(List.of(new ReservationDto(5L, 2L, from,
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Ana", "x")));

        mvc.perform(get("/api/reservas").param("salaId", "2")
                        .param("fechaDesde", "2030-05-10").param("fechaHasta", "2030-05-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].salaId").value(2));
    }

    @Test
    void reservarParaHoyDa400ConCampoFecha() throws Exception {
        when(service.create(any())).thenThrow(new InvalidReservationException("fecha", "debe ser posterior a hoy"));

        enviar(VALID)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"));
    }
}
