package com.reservasalas.exception;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reservasalas.controller.ReservationController;
import com.reservasalas.controller.RoomController;
import com.reservasalas.service.ReservationService;
import com.reservasalas.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({RoomController.class, ReservationController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RoomService roomService;

    @MockitoBean
    private ReservationService reservationService;

    @ParameterizedTest
    @ValueSource(strings = {"/api/no-existe", "/api/reservas/7", "/api/salas/1/otra"})
    void rutaInexistenteDevuelve404ProblemDetailGenerico(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail").value("El recurso solicitado no existe"))
                .andExpect(content().string(not(containsString("static resource"))));
    }

    @Test
    void metodoNoPermitidoDevuelve405ProblemDetailConAllow() throws Exception {
        mvc.perform(put("/api/salas"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").value("Método no permitido"))
                .andExpect(jsonPath("$.detail").value("El método HTTP no está permitido en este recurso"))
                .andExpect(content().string(not(containsString("PUT"))));
    }

    @Test
    void tipoDeContenidoNoSoportadoDevuelve415ProblemDetail() throws Exception {
        mvc.perform(post("/api/reservas").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"))
                .andExpect(jsonPath("$.detail").value("El tipo de contenido de la petición no está soportado"))
                .andExpect(content().string(not(containsString("text/plain"))));
    }

    @Test
    void error404IncluyeInstanceConLaRutaPeroNoEnDetailNiTitle() throws Exception {
        mvc.perform(get("/api/no-existe"))
                .andExpect(jsonPath("$.instance").value("/api/no-existe"))
                .andExpect(jsonPath("$.detail").value(not(containsString("no-existe"))))
                .andExpect(jsonPath("$.title").value(not(containsString("no-existe"))))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void metodosNoPermitidosEnSalasDevuelven405ConInstanceYSinMetodoEnElCuerpo(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/api/salas"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").value("Método no permitido"))
                .andExpect(jsonPath("$.instance").value("/api/salas"))
                .andExpect(content().string(not(containsString(method))))
                .andExpect(content().string(not(containsString("Request method"))));
    }

    @Test
    void metodoNoPermitidoEnReservasIncluyeGetYPostEnAllow() throws Exception {
        mvc.perform(delete("/api/reservas"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Allow", allOf(containsString("GET"), containsString("POST"))))
                .andExpect(jsonPath("$.instance").value("/api/reservas"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/xml", "application/x-www-form-urlencoded"})
    void tiposDeContenidoNoSoportadosDevuelven415ConInstanceYSinTipoEnElCuerpo(String type) throws Exception {
        mvc.perform(post("/api/reservas").contentType(type).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"))
                .andExpect(jsonPath("$.instance").value("/api/reservas"))
                .andExpect(content().string(not(containsString(type))))
                .andExpect(content().string(not(containsString("Content-Type"))))
                .andExpect(content().string(not(containsString("application/json"))));
    }

    @Test
    void errorDe405NoAfectaAlGetValido() throws Exception {
        mvc.perform(get("/api/salas")).andExpect(status().isOk());
    }
}
