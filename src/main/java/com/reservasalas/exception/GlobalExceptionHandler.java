package com.reservasalas.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.dto.FieldErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/**
 * Traduce las excepciones de la aplicación a respuestas {@link ProblemDetail} (RFC 9457).
 *
 * <p>Los errores de validación ({@code 400}) incluyen la propiedad {@code errores} con la lista de
 * campos incorrectos, usando los nombres de campo de la API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERRORS_PROPERTY = "errores";
    private static final String VALIDATION_TITLE = "Datos no válidos";
    private static final String VALIDATION_DETAIL = "La petición contiene datos no válidos";
    private static final Pattern NON_FIELD_CHARS = Pattern.compile("[^A-Za-z0-9_]");
    private static final int MAX_LOGGED_FIELD_LENGTH = 64;

    /**
     * Sala no encontrada.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 404}
     */
    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleRoomNotFound(RoomNotFoundException ex, HttpServletRequest request) {
        LOG.warn("Sala no encontrada: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Sala no encontrada", ex.getMessage(), request);
    }

    /**
     * Reserva solapada con otra de la misma sala.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 409}
     */
    @ExceptionHandler(OverlappingReservationException.class)
    public ResponseEntity<ProblemDetail> handleOverlapping(OverlappingReservationException ex,
                                                           HttpServletRequest request) {
        LOG.warn("Reserva solapada: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Reserva solapada", ex.getMessage(), request);
    }

    /**
     * Regla de negocio incumplida en un campo concreto (alta de reserva o filtro de búsqueda).
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 400} con la lista de errores
     */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ProblemDetail> handleFieldValidation(FieldValidationException ex,
                                                               HttpServletRequest request) {
        LOG.warn("Dato no válido en el campo {}: {}", ex.getField(), ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_TITLE,
                ex.getField() + " " + ex.getMessage(), request.getRequestURI());
        problem.setProperty(ERRORS_PROPERTY, List.of(new FieldErrorDto(ex.getField(), ex.getMessage())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Red de seguridad para cualquier error no previsto: se registra la traza y no se filtran
     * detalles internos al cliente.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 500}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOG.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Se ha producido un error inesperado", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Class<?> target = ex.getParameter().getParameterType();
        List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> toFieldError(target, error))
                .toList();
        LOG.warn("Petición con datos no válidos: {}", errors);
        return validationResponse(errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String field = null;
        if (ex.getCause() instanceof JacksonException jacksonException && !jacksonException.getPath().isEmpty()) {
            field = jacksonException.getPath().getLast().getPropertyName();
        }
        LOG.warn("Cuerpo de la petición no legible ({}) en el campo {}", causeName(ex), sanitizeForLog(field));
        if (field != null) {
            return validationResponse(List.of(new FieldErrorDto(field, "tiene un formato no válido")),
                    headers, request);
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_TITLE,
                "El cuerpo de la petición no es un JSON válido", requestUri(request));
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        LOG.warn("Parámetro con tipo no válido: {}", ex.getPropertyName());
        String field = ex.getPropertyName() == null ? "desconocido" : ex.getPropertyName();
        return validationResponse(List.of(new FieldErrorDto(field, "tiene un formato no válido")),
                headers, request);
    }

    /**
     * Deja el nombre de un campo apto para el log: solo caracteres {@code [A-Za-z0-9_]} y longitud
     * acotada, para evitar log forging con nombres de propiedad arbitrarios del cuerpo.
     */
    private static String sanitizeForLog(String field) {
        if (field == null) {
            return "desconocido";
        }
        String clean = NON_FIELD_CHARS.matcher(field).replaceAll("");
        if (clean.length() > MAX_LOGGED_FIELD_LENGTH) {
            clean = clean.substring(0, MAX_LOGGED_FIELD_LENGTH);
        }
        return clean.isEmpty() ? "desconocido" : clean;
    }

    /**
     * Nombre simple de la clase de la causa (o de la propia excepción si no tiene causa), sin su
     * mensaje, que puede contener fragmentos del cuerpo.
     */
    private static String causeName(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        return cause.getClass().getSimpleName();
    }

    /**
     * Construye la respuesta {@code 400} con la lista de campos incorrectos.
     */
    private ResponseEntity<Object> validationResponse(List<FieldErrorDto> errors, HttpHeaders headers,
                                                      WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_TITLE, VALIDATION_DETAIL,
                requestUri(request));
        problem.setProperty(ERRORS_PROPERTY, errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /**
     * Traduce un error de Bean Validation al nombre de campo que ve el cliente: el del
     * {@link JsonProperty} del componente si lo tiene, o el nombre Java en su defecto.
     */
    private static FieldErrorDto toFieldError(Class<?> target, FieldError error) {
        String field = error.getField();
        try {
            JsonProperty property = target.getDeclaredField(field).getAnnotation(JsonProperty.class);
            if (property != null && !property.value().isEmpty()) {
                field = property.value();
            }
        } catch (NoSuchFieldException e) {
            LOG.debug("El campo {} no existe en {}; se usa su nombre Java", field, target.getSimpleName());
        }
        return new FieldErrorDto(field, error.getDefaultMessage());
    }

    private static ResponseEntity<ProblemDetail> build(HttpStatus status, String title, String detail,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(status).body(problem(status, title, detail, request.getRequestURI()));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String uri) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        if (uri != null) {
            problem.setInstance(URI.create(uri));
        }
        return problem;
    }

    private static String requestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return null;
    }
}
