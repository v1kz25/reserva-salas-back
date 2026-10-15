package com.reservasalas.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Errores sobrescritos de {@link GlobalExceptionHandler} que ningún controlador real puede provocar
 * (parámetro obligatorio, cabecera, parte multipart, validación de parámetros, ResponseStatusException),
 * probados extremo a extremo con un controlador de test.
 */
@WebMvcTest(controllers = ErroresSobrescritosWebTest.ControladorDePrueba.class,
        properties = "logging.level.com.reservasalas=INFO")
@Import({GlobalExceptionHandler.class, ErroresSobrescritosWebTest.ControladorDePrueba.class})
@ExtendWith(OutputCaptureExtension.class)
class ErroresSobrescritosWebTest {

    @RestController
    @RequestMapping("/prueba")
    static class ControladorDePrueba {

        @GetMapping("/param")
        String param(@RequestParam("fechaDesde") String desde) {
            return desde;
        }

        @GetMapping("/cabecera")
        String cabecera(@RequestHeader("X-Obligatoria") String valor) {
            return valor;
        }

        @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
        String json() {
            return "{}";
        }

        @GetMapping("/validado")
        String validado(@RequestParam("cantidad") @Min(value = 1, message = "debe ser al menos 1") int cantidad) {
            return String.valueOf(cantidad);
        }

        @GetMapping("/rse")
        String rse() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "motivo-interno-secreto");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("centinela-interno-123");
        }

        @RequestMapping(path = "/parte", method = RequestMethod.POST)
        String parte(@RequestPart("adjunto") MultipartFile adjunto) {
            return adjunto.getName();
        }

        @GetMapping("/tipo/{id}")
        String tipo(@PathVariable Long id) {
            return String.valueOf(id);
        }
    }

    @Autowired
    private MockMvc mvc;

    private static ResultActions sinTextoInterno(ResultActions r) throws Exception {
        return r.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.springframework"))))
                .andExpect(content().string(not(containsString("java."))))
                .andExpect(content().string(not(containsString("com.reservasalas"))))
                .andExpect(content().string(not(containsString("Required"))))
                .andExpect(content().string(not(containsString("is not present"))))
                .andExpect(content().string(not(containsString("Failed"))));
    }

    @Test
    void parametroObligatorioAusenteDevuelve400ConErrores() throws Exception {
        sinTextoInterno(mvc.perform(get("/prueba/param")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.detail").value("La petición contiene datos no válidos"))
                .andExpect(jsonPath("$.instance").value("/prueba/param"))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("fechaDesde"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("es obligatorio"));
    }

    @Test
    void parametroPresenteNoDaError() throws Exception {
        mvc.perform(get("/prueba/param").param("fechaDesde", "x")).andExpect(status().isOk());
    }

    @Test
    void parametroConTipoNoValidoNoFiltraElValorRecibido() throws Exception {
        sinTextoInterno(mvc.perform(get("/prueba/tipo/valor-secreto-abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("tiene un formato no válido"))
                .andExpect(jsonPath("$.detail").value(not(containsString("valor-secreto-abc"))))
                .andExpect(jsonPath("$.errores[0].mensaje").value(not(containsString("valor-secreto-abc"))));
    }

    @Test
    void cabeceraObligatoriaAusenteDevuelve400Generico() throws Exception {
        sinTextoInterno(mvc.perform(get("/prueba/cabecera")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.detail").value("La petición no tiene el formato esperado"))
                .andExpect(jsonPath("$.instance").value("/prueba/cabecera"))
                .andExpect(content().string(not(containsString("X-Obligatoria"))));
    }

    @Test
    void parteMultipartAusenteDevuelve400Generico() throws Exception {
        sinTextoInterno(mvc.perform(multipart("/prueba/parte")
                        .file("otra-parte-secreta", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Petición no válida"))
                .andExpect(jsonPath("$.detail").value("La petición no tiene el formato esperado"))
                .andExpect(content().string(not(containsString("adjunto"))));
    }

    @Test
    void formatoNoAceptableDevuelve406() throws Exception {
        sinTextoInterno(mvc.perform(get("/prueba/json").accept(MediaType.APPLICATION_XML)))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.title").value("No aceptable"))
                .andExpect(jsonPath("$.detail")
                        .value("No se puede generar la respuesta en ninguno de los formatos aceptados"))
                .andExpect(jsonPath("$.instance").value("/prueba/json"))
                .andExpect(content().string(not(containsString("application/xml"))))
                .andExpect(content().string(not(containsString("application/json"))));
    }

    @Test
    void validacionDeParametroUsaElNombreDeLaApi() throws Exception {
        sinTextoInterno(mvc.perform(get("/prueba/validado").param("cantidad", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos no válidos"))
                .andExpect(jsonPath("$.detail").value("La petición contiene datos no válidos"))
                .andExpect(jsonPath("$.errores[0].campo").value("cantidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").isNotEmpty())
                .andExpect(content().string(not(containsString("arg0"))))
                .andExpect(jsonPath("$.errores[0].campo").value(not(containsString("validado"))));
    }

    @Test
    void responseStatusExceptionConservaElEstadoConTextosGenericos() throws Exception {
        sinTextoInterno(mvc.perform(get("/prueba/rse")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Petición rechazada"))
                .andExpect(jsonPath("$.detail").value("No se puede procesar la petición"))
                .andExpect(content().string(not(containsString("motivo-interno-secreto"))));
    }

    @Test
    void elLogDelParametroAusenteNoIncluyeRutaNiMetodo(CapturedOutput output) throws Exception {
        mvc.perform(get("/prueba/param").header("X-Marcador", "marcador-xyz"))
                .andExpect(status().isBadRequest());

        assertSinDatosDePeticion(output, "/prueba/param", "Falta el parámetro obligatorio fechaDesde");
    }

    @Test
    void elLogDeLaCabeceraAusenteNoIncluyeRutaNiMetodo(CapturedOutput output) throws Exception {
        mvc.perform(get("/prueba/cabecera")).andExpect(status().isBadRequest());

        assertSinDatosDePeticion(output, "/prueba/cabecera", "La petición no cumple los requisitos del endpoint");
    }

    @Test
    void elLogDeResponseStatusExceptionNoIncluyeRutaNiMotivo(CapturedOutput output) throws Exception {
        mvc.perform(get("/prueba/rse")).andExpect(status().isForbidden());

        assertSinDatosDePeticion(output, "/prueba/rse", "Petición rechazada con estado 403");
        assertThat(output.getAll()).doesNotContain("motivo-interno-secreto");
    }

    @Test
    void elLogDeLaValidacionDeParametrosNoIncluyeRutaNiMetodo(CapturedOutput output) throws Exception {
        mvc.perform(get("/prueba/validado").param("cantidad", "0")).andExpect(status().isBadRequest());

        assertSinDatosDePeticion(output, "/prueba/validado", "Parámetros de la petición no válidos");
    }

    private static void assertSinDatosDePeticion(CapturedOutput output, String path, String mensajeEsperado) {
        assertThat(output.getAll())
                .contains(mensajeEsperado)
                .doesNotContain(path)
                .doesNotContain("GET /prueba")
                .doesNotContain("marcador-xyz");
    }

    @Test
    void error500InesperadoNoDevuelveDetallesYElLogNoIncluyeRutaNiMetodo(CapturedOutput output) throws Exception {
        sinTextoInterno(mvc.perform(get("/prueba/boom")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.detail").value("Se ha producido un error inesperado"))
                .andExpect(content().string(not(containsString("centinela-interno-123"))));

        assertThat(output.getAll())
                .contains("Error inesperado")
                .contains("IllegalStateException")
                .doesNotContain("/prueba/boom")
                .doesNotContain("GET /prueba")
                .doesNotContain("centinela-interno-123");
    }
}
