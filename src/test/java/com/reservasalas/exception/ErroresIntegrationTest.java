package com.reservasalas.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Errores de infraestructura (404, 405, 415) con la aplicación completa: recursos estáticos reales,
 * manejador global y log.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:errores-${random.uuid}",
        "logging.level.com.reservasalas=DEBUG"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ErroresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/no-existe", "/api/salas/1/otra", "/api/reservas/7", "/no-existe", "/api/"})
    void rutaInexistenteDevuelve404ProblemDetailSinDetallesInternos(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.detail").value("El recurso solicitado no existe"))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(content().string(not(containsString("static resource"))))
                .andExpect(content().string(not(containsString("No static"))))
                .andExpect(content().string(not(containsString("org.springframework"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/no-existe", "/api/salas/1"})
    void postEnRutaSinControladorDa404Generico(String path) throws Exception {
        // Solo existen GET /api/salas, GET /api/reservas y POST /api/reservas: no hay rutas con
        // PUT/DELETE, y el manejador de recursos estáticos responde 404 (no 405) a un POST.
        mvc.perform(post(path))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No encontrado"))
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(content().string(not(containsString("static resource"))));
    }

    @Test
    void metodoNoPermitidoDevuelve405ConAllow() throws Exception {
        mvc.perform(delete("/api/salas"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.title").value("Método no permitido"))
                .andExpect(jsonPath("$.instance").value("/api/salas"));
    }

    @Test
    void tipoDeContenidoNoSoportadoDevuelve415() throws Exception {
        mvc.perform(post("/api/reservas").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Tipo de contenido no soportado"))
                .andExpect(jsonPath("$.instance").value("/api/reservas"));
    }

    @Test
    void elLogNoContieneRutaMetodoNiContentTypeDeLasPeticionesErroneas(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/ruta-secreta-xyz")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/salas")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/reservas").contentType("application/x-marcador-xyz").content("x"))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(output.getAll())
                .contains("Petición a una ruta inexistente")
                .contains("Método HTTP no permitido en la ruta solicitada")
                .contains("Tipo de contenido de la petición no soportado")
                .doesNotContain("ruta-secreta-xyz")
                .doesNotContain("x-marcador-xyz")
                .doesNotContain("DELETE /api/salas")
                .doesNotContain("Request method")
                .doesNotContain("No static resource");
    }

    @Test
    void erroresExistentesNoHanCambiado() throws Exception {
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.detail").value("La petición contiene datos no válidos"))
                .andExpect(jsonPath("$.errores").isNotEmpty());

        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("{no json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición no es un JSON válido"));

        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"salaId":999999,"fecha":"2099-05-10","horaInicio":"10:00","horaFin":"11:00",
                         "responsable":"Ana","motivo":"Reunión"}"""))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Sala no encontrada"))
                .andExpect(jsonPath("$.instance").value("/api/reservas"));

        mvc.perform(get("/api/reservas").param("salaId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("salaId"));
    }
}
