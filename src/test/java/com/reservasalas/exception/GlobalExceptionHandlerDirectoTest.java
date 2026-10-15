package com.reservasalas.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Casos sobrescritos de {@link GlobalExceptionHandler} que no se pueden provocar de forma realista
 * por HTTP (503 asíncrono, 413, 500 internos, variable de ruta ausente...): se invoca el manejador
 * directamente.
 */
class GlobalExceptionHandlerDirectoTest {

    private static final String VALOR_RECIBIDO = "valor-secreto-xyz";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest servletRequest = new MockHttpServletRequest("PUT", "/ruta/interna-secreta");
    private final ServletWebRequest request = new ServletWebRequest(servletRequest);

    @SuppressWarnings("unused")
    private void metodoDeEjemplo(Long id) {
    }

    private MethodParameter parametro() throws Exception {
        Method method = getClass().getDeclaredMethod("metodoDeEjemplo", Long.class);
        return new MethodParameter(method, 0);
    }

    private ProblemDetail verifica(ResponseEntity<Object> response, HttpStatus status, String title, String detail) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getStatus()).isEqualTo(status.value());
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getDetail()).isEqualTo(detail);
        assertThat(problem.getInstance()).hasToString("/ruta/interna-secreta");
        String texto = problem.toString();
        assertThat(texto).doesNotContain("Exception", "org.springframework", "java.", VALOR_RECIBIDO, "PUT");
        return problem;
    }

    @Test
    void error406DirectoEsGenerico() {
        var ex = new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));
        verifica(handler.handleHttpMediaTypeNotAcceptable(ex, new HttpHeaders(), HttpStatus.NOT_ACCEPTABLE, request),
                HttpStatus.NOT_ACCEPTABLE, "No aceptable",
                "No se puede generar la respuesta en ninguno de los formatos aceptados");
    }

    @Test
    void parametroAusenteDirectoDevuelveErroresConCampoYMensaje() {
        var ex = new MissingServletRequestParameterException("fechaDesde", "LocalDate");
        ProblemDetail problem = verifica(handler.handleMissingServletRequestParameter(ex, new HttpHeaders(),
                        HttpStatus.BAD_REQUEST, request),
                HttpStatus.BAD_REQUEST, "Datos no válidos", "La petición contiene datos no válidos");
        assertThat(problem.getProperties()).containsKey("errores");
        assertThat(problem.getProperties().get("errores").toString())
                .contains("fechaDesde", "es obligatorio")
                .doesNotContain("LocalDate");
    }

    @Test
    void variableDeRutaAusenteConEstado400EsGenerica() throws Exception {
        var ex = new MissingPathVariableException(VALOR_RECIBIDO, parametro());
        verifica(handler.handleMissingPathVariable(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request),
                HttpStatus.BAD_REQUEST, "Petición no válida", "La petición no tiene el formato esperado");
    }

    @Test
    void variableDeRutaAusenteConEstado500EsGenerica() throws Exception {
        var ex = new MissingPathVariableException(VALOR_RECIBIDO, parametro());
        verifica(handler.handleMissingPathVariable(ex, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR,
                        request),
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Se ha producido un error inesperado");
    }

    @Test
    void metodoNoPermitidoConservaLaCabeceraAllow() {
        var ex = new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "POST"));
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(java.util.Set.of(HttpMethod.GET, HttpMethod.POST));
        ResponseEntity<Object> response = handler.handleHttpRequestMethodNotSupported(ex, headers,
                HttpStatus.METHOD_NOT_ALLOWED, request);
        verifica(response, HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "El método HTTP no está permitido en este recurso");
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void partesMultipartAusentesEsGenerico() {
        var ex = new MissingServletRequestPartException(VALOR_RECIBIDO);
        verifica(handler.handleMissingServletRequestPart(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST, request),
                HttpStatus.BAD_REQUEST, "Petición no válida", "La petición no tiene el formato esperado");
    }

    @Test
    void bindingExceptionEsGenerica() {
        var ex = new ServletRequestBindingException("Missing header " + VALOR_RECIBIDO);
        verifica(handler.handleServletRequestBindingException(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST,
                        request),
                HttpStatus.BAD_REQUEST, "Petición no válida", "La petición no tiene el formato esperado");
    }

    @Test
    void validacionDeHandlerMethodConEstado500EsGenerica() throws Exception {
        var ex = mock(HandlerMethodValidationException.class);
        when(ex.getMethod()).thenReturn(getClass().getDeclaredMethod("metodoDeEjemplo", Long.class));
        verifica(handler.handleHandlerMethodValidationException(ex, new HttpHeaders(),
                        HttpStatus.INTERNAL_SERVER_ERROR, request),
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Se ha producido un error inesperado");
    }

    @Test
    void timeoutAsincronoDevuelve503Generico() {
        verifica(handler.handleAsyncRequestTimeoutException(new AsyncRequestTimeoutException(), new HttpHeaders(),
                        HttpStatus.SERVICE_UNAVAILABLE, request),
                HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "El servicio no está disponible temporalmente; inténtalo de nuevo más tarde");
    }

    @Test
    void cuerpoDemasiadoGrandeDevuelve413Generico() {
        var ex = new MaxUploadSizeExceededException(1024);
        verifica(handler.handleMaxUploadSizeExceededException(ex, new HttpHeaders(), HttpStatus.CONTENT_TOO_LARGE,
                        request),
                HttpStatus.CONTENT_TOO_LARGE, "Contenido demasiado grande",
                "El cuerpo de la petición supera el tamaño máximo permitido");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "400|Petición no válida|La petición no tiene el formato esperado",
        "401|Petición rechazada|No se puede procesar la petición",
        "403|Petición rechazada|No se puede procesar la petición",
        "404|No encontrado|El recurso solicitado no existe",
        "409|Conflicto|La petición entra en conflicto con el estado actual del recurso",
        "418|Petición rechazada|No se puede procesar la petición",
        "500|Error interno|Se ha producido un error inesperado",
        "502|Error interno|Se ha producido un error inesperado"})
    void errorResponseExceptionConservaElEstadoConTextosGenericos(int code, String title, String detail) {
        var ex = new ResponseStatusException(HttpStatus.valueOf(code), VALOR_RECIBIDO);
        ResponseEntity<Object> response = handler.handleErrorResponseException(ex, new HttpHeaders(),
                ex.getStatusCode(), request);
        assertThat(response.getStatusCode().value()).isEqualTo(code);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(code);
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getDetail()).isEqualTo(detail);
    }

    @Test
    void errorResponseExceptionGenericaNoFiltraLaCausa() {
        var ex = new ErrorResponseException(HttpStatus.CONFLICT, new IllegalStateException(VALOR_RECIBIDO));
        ResponseEntity<Object> response = handler.handleErrorResponseException(ex, new HttpHeaders(),
                HttpStatus.CONFLICT, request);
        verifica(response, HttpStatus.CONFLICT, "Conflicto",
                "La petición entra en conflicto con el estado actual del recurso");
    }

    @Test
    void conversionNoSoportadaDevuelve500Generico() {
        var ex = new ConversionNotSupportedException(VALOR_RECIBIDO, Long.class, new IllegalStateException(VALOR_RECIBIDO));
        verifica(handler.handleConversionNotSupported(ex, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR,
                        request),
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Se ha producido un error inesperado");
    }

    @Test
    void mensajeNoEscribibleDevuelve500Generico() {
        var ex = new HttpMessageNotWritableException(VALOR_RECIBIDO, new IllegalStateException(VALOR_RECIBIDO));
        verifica(handler.handleHttpMessageNotWritable(ex, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR,
                        request),
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Se ha producido un error inesperado");
    }

    @Test
    void validacionDeMetodoDevuelve500Generico() throws Exception {
        var ex = mock(MethodValidationException.class);
        when(ex.getMethod()).thenReturn(getClass().getDeclaredMethod("metodoDeEjemplo", Long.class));
        verifica(handler.handleMethodValidationException(ex, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR,
                        request),
                HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Se ha producido un error inesperado");
    }
}
