package com.reservasalas.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.reservasalas.dto.FieldErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;

/**
 * Traduce las excepciones de la aplicación a respuestas {@link ProblemDetail} (RFC 9457).
 *
 * <p>Los errores de validación ({@code 400}) incluyen la propiedad {@code errores} con la lista de
 * campos incorrectos, usando los nombres de campo de la API. El resto de errores que gestiona
 * {@link ResponseEntityExceptionHandler} (ruta inexistente, método o tipo de contenido no soportados,
 * formato no aceptable, cuerpo demasiado grande, tiempo agotado...) devuelven un título y un detalle
 * genéricos en español, sin nombres de clases, rutas internas ni valores recibidos. El log tampoco
 * registra la ruta, el método, las cabeceras ni el cuerpo de la petición.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERRORS_PROPERTY = "errores";
    private static final String VALIDATION_TITLE = "Datos no válidos";
    private static final String VALIDATION_DETAIL = "La petición contiene datos no válidos";
    private static final Pattern NON_FIELD_CHARS = Pattern.compile("[^A-Za-z0-9_]");
    private static final int MAX_LOGGED_FIELD_LENGTH = 64;
    private static final String REQUIRED_MESSAGE = "es obligatorio";
    private static final String INVALID_FORMAT_MESSAGE = "tiene un formato no válido";
    private static final String INVALID_VALUE_MESSAGE = "no es válido";
    private static final String UNKNOWN_FIELD = "desconocido";
    private static final String UNAUTHENTICATED_TITLE = "No autenticado";
    private static final String UNAUTHENTICATED_DETAIL = "Debes iniciar sesión para acceder a este recurso";
    private static final String FORBIDDEN_TITLE = "Acceso denegado";
    private static final String FORBIDDEN_DETAIL = "No tienes permiso para realizar esta operación";

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
     * Reserva no encontrada.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 404}
     */
    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleReservationNotFound(ReservationNotFoundException ex,
                                                                   HttpServletRequest request) {
        LOG.warn("Reserva no encontrada: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Reserva no encontrada", ex.getMessage(), request);
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
     * Nombre de sala repetido al crear o modificar una sala.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 409}
     */
    @ExceptionHandler(DuplicateRoomNameException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateRoomName(DuplicateRoomNameException ex,
                                                                 HttpServletRequest request) {
        LOG.warn("Nombre de sala repetido");
        return build(HttpStatus.CONFLICT, "Nombre de sala repetido", ex.getMessage(), request);
    }

    /**
     * Eliminación de una sala que tiene reservas de hoy o posteriores.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 409}
     */
    @ExceptionHandler(RoomHasFutureReservationsException.class)
    public ResponseEntity<ProblemDetail> handleRoomHasFutureReservations(RoomHasFutureReservationsException ex,
                                                                         HttpServletRequest request) {
        LOG.warn("Sala con reservas futuras: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Sala con reservas futuras", ex.getMessage(), request);
    }

    /**
     * Usuario no encontrado.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 404}
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        LOG.warn("Usuario no encontrado: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Usuario no encontrado", ex.getMessage(), request);
    }

    /**
     * Email repetido al crear un usuario. No se registra el email.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 409}
     */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateEmail(DuplicateEmailException ex,
                                                              HttpServletRequest request) {
        LOG.warn("Email de usuario repetido");
        return build(HttpStatus.CONFLICT, "Email repetido", ex.getMessage(), request);
    }

    /**
     * Administrador que intenta desactivarse o quitarse el rol a sí mismo.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 409}
     */
    @ExceptionHandler(SelfDemotionException.class)
    public ResponseEntity<ProblemDetail> handleSelfDemotion(SelfDemotionException ex, HttpServletRequest request) {
        LOG.warn("Un administrador ha intentado desactivarse o quitarse el rol");
        return build(HttpStatus.CONFLICT, "Cambio no permitido sobre uno mismo", ex.getMessage(), request);
    }

    /**
     * Cambio que dejaría la aplicación sin ningún administrador activo.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 409}
     */
    @ExceptionHandler(LastActiveAdminException.class)
    public ResponseEntity<ProblemDetail> handleLastActiveAdmin(LastActiveAdminException ex,
                                                               HttpServletRequest request) {
        LOG.warn("Cambio rechazado: dejaría la aplicación sin administradores activos");
        return build(HttpStatus.CONFLICT, "Sin administradores activos", ex.getMessage(), request);
    }

    /**
     * Dos modificaciones simultáneas del mismo usuario: la que llega tarde se rechaza. Solo los
     * usuarios llevan bloqueo optimista.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 409} genérica
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLocking(OptimisticLockingFailureException ex,
                                                                 HttpServletRequest request) {
        LOG.warn("Modificación simultánea rechazada ({})", ex.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "Modificación simultánea",
                "El usuario se ha modificado a la vez; vuelve a intentarlo", request);
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
     * Login con credenciales no válidas. El mensaje es el mismo si el email no existe o si la
     * contraseña es incorrecta.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 401}
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex,
                                                                  HttpServletRequest request) {
        LOG.warn("Inicio de sesión con credenciales no válidas");
        return build(HttpStatus.UNAUTHORIZED, "Credenciales no válidas", ex.getMessage(), request);
    }

    /**
     * Petición sin sesión (o con la sesión caducada) a un recurso que la exige. Llega desde el filtro
     * de seguridad a través de su punto de entrada.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 401} genérica
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleUnauthenticated(AuthenticationException ex,
                                                               HttpServletRequest request) {
        LOG.debug("Petición sin sesión válida ({})", ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, UNAUTHENTICATED_TITLE, UNAUTHENTICATED_DETAIL, request);
    }

    /**
     * Operación sin permiso: rol insuficiente, reserva de otro usuario o token CSRF ausente o no
     * válido. No se distingue el motivo en la respuesta.
     *
     * @param ex      excepción capturada
     * @param request petición en curso
     * @return respuesta {@code 403} genérica
     */
    @ExceptionHandler({AccessDeniedException.class, ForbiddenOperationException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(RuntimeException ex, HttpServletRequest request) {
        LOG.warn("Acceso denegado ({})", ex.getClass().getSimpleName());
        return build(HttpStatus.FORBIDDEN, FORBIDDEN_TITLE, FORBIDDEN_DETAIL, request);
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
        logError("Error inesperado", ex);
        GenericError error = GenericError.of(HttpStatus.INTERNAL_SERVER_ERROR);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, error.title(), error.detail(), request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        LOG.debug("Petición a una ruta inexistente");
        return genericResponse(ex, HttpStatus.NOT_FOUND, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(NoHandlerFoundException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        LOG.debug("Petición sin controlador asociado");
        return genericResponse(ex, HttpStatus.NOT_FOUND, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers,
                                                                         HttpStatusCode status,
                                                                         WebRequest request) {
        LOG.warn("Método HTTP no permitido en la ruta solicitada");
        return genericResponse(ex, HttpStatus.METHOD_NOT_ALLOWED, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpHeaders headers,
                                                                     HttpStatusCode status,
                                                                     WebRequest request) {
        LOG.warn("Tipo de contenido de la petición no soportado");
        return genericResponse(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                                      HttpHeaders headers,
                                                                      HttpStatusCode status,
                                                                      WebRequest request) {
        LOG.warn("Ningún formato de respuesta es aceptable para el cliente");
        return genericResponse(ex, HttpStatus.NOT_ACCEPTABLE, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(MissingPathVariableException ex,
                                                               HttpHeaders headers,
                                                               HttpStatusCode status,
                                                               WebRequest request) {
        if (status.is5xxServerError()) {
            LOG.error("La variable de ruta {} no está declarada en la plantilla de la ruta", ex.getVariableName());
        } else {
            LOG.warn("Variable de ruta {} ausente tras la conversión", ex.getVariableName());
        }
        return genericResponse(ex, status, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        LOG.warn("Falta el parámetro obligatorio {}", ex.getParameterName());
        return validationResponse(ex, List.of(new FieldErrorDto(ex.getParameterName(), REQUIRED_MESSAGE)),
                headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex,
                                                                     HttpHeaders headers,
                                                                     HttpStatusCode status,
                                                                     WebRequest request) {
        LOG.warn("Falta una parte obligatoria de la petición multipart");
        return genericResponse(ex, HttpStatus.BAD_REQUEST, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        LOG.warn("La petición no cumple los requisitos del endpoint ({})", ex.getClass().getSimpleName());
        return genericResponse(ex, status, headers, request);
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
        return validationResponse(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        if (status.is5xxServerError()) {
            LOG.error("La respuesta de {} no cumple sus restricciones de validación", ex.getMethod().getName());
            return genericResponse(ex, status, headers, request);
        }
        List<FieldErrorDto> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> toFieldErrors(result).stream())
                .toList();
        LOG.warn("Parámetros de la petición no válidos: {}", errors);
        return validationResponse(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex,
                                                                        HttpHeaders headers,
                                                                        HttpStatusCode status,
                                                                        WebRequest request) {
        LOG.warn("Tiempo de espera agotado al procesar una petición asíncrona");
        return genericResponse(ex, HttpStatus.SERVICE_UNAVAILABLE, headers, request);
    }

    /**
     * Descarta a propósito el {@link ProblemDetail} que trae la propia excepción (su título y detalle
     * pueden estar en inglés o incluir el valor recibido) y responde con el genérico de su estado.
     */
    @Override
    protected ResponseEntity<Object> handleErrorResponseException(ErrorResponseException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        LOG.warn("Petición rechazada con estado {} ({})", status.value(), ex.getClass().getSimpleName());
        return genericResponse(ex, status, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        LOG.warn("Cuerpo de la petición mayor que el tamaño máximo permitido");
        return genericResponse(ex, HttpStatus.CONTENT_TOO_LARGE, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleConversionNotSupported(ConversionNotSupportedException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        LOG.error("No hay conversor para el parámetro {} al tipo {}", ex.getPropertyName(),
                ex.getRequiredType() == null ? UNKNOWN_FIELD : ex.getRequiredType().getSimpleName());
        return genericResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotWritable(HttpMessageNotWritableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        logError("No se ha podido escribir la respuesta", ex);
        return genericResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodValidationException(MethodValidationException ex,
                                                                     HttpHeaders headers,
                                                                     HttpStatus status,
                                                                     WebRequest request) {
        LOG.error("Validación de método fallida en {}.{}", ex.getMethod().getDeclaringClass().getSimpleName(),
                ex.getMethod().getName());
        return genericResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR, headers, request);
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
            return validationResponse(ex, List.of(new FieldErrorDto(field, INVALID_FORMAT_MESSAGE)),
                    headers, request);
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_TITLE,
                "El cuerpo de la petición no es un JSON válido", requestUri(request));
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
                                                        HttpHeaders headers,
                                                        HttpStatusCode status,
                                                        WebRequest request) {
        LOG.warn("Parámetro con tipo no válido: {}", ex.getPropertyName());
        String field = ex.getPropertyName() == null ? UNKNOWN_FIELD : ex.getPropertyName();
        return validationResponse(ex, List.of(new FieldErrorDto(field, INVALID_FORMAT_MESSAGE)),
                headers, request);
    }

    /**
     * Registra un error sin datos de la petición: a nivel ERROR, un mensaje fijo y el nombre simple de
     * la clase de la excepción; la traza completa, cuyo mensaje puede incluir SQL, valores o texto del
     * cuerpo, solo a nivel DEBUG.
     */
    private static void logError(String message, Exception ex) {
        LOG.error("{} ({})", message, ex.getClass().getSimpleName());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Traza de: {}", message, ex);
        }
    }

    /**
     * Deja el nombre de un campo apto para el log: solo caracteres {@code [A-Za-z0-9_]} y longitud
     * acotada, para evitar log forging con nombres de propiedad arbitrarios del cuerpo.
     */
    private static String sanitizeForLog(String field) {
        if (field == null) {
            return UNKNOWN_FIELD;
        }
        String clean = NON_FIELD_CHARS.matcher(field).replaceAll("");
        if (clean.length() > MAX_LOGGED_FIELD_LENGTH) {
            clean = clean.substring(0, MAX_LOGGED_FIELD_LENGTH);
        }
        return clean.isEmpty() ? UNKNOWN_FIELD : clean;
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
     * Construye la respuesta genérica del estado indicado: título y detalle en español que no exponen
     * nada de la petición. Conserva las cabeceras que aporte Spring (por ejemplo, {@code Allow}).
     */
    private ResponseEntity<Object> genericResponse(Exception ex, HttpStatusCode status, HttpHeaders headers,
                                                   WebRequest request) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved == null) {
            resolved = status.is5xxServerError() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        }
        GenericError error = GenericError.of(resolved);
        return buildInherited(ex, resolved, error.title(), error.detail(), headers, request);
    }

    /**
     * Construye la respuesta {@code 400} con la lista de campos incorrectos.
     */
    private ResponseEntity<Object> validationResponse(Exception ex, List<FieldErrorDto> errors,
                                                      HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, VALIDATION_TITLE, VALIDATION_DETAIL,
                requestUri(request));
        problem.setProperty(ERRORS_PROPERTY, errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Traduce el resultado de validación de un parámetro del controlador a errores de campo: si el
     * parámetro es un objeto validado, un error por campo; si es un valor simple, un error con el
     * nombre del parámetro en la API.
     */
    private static List<FieldErrorDto> toFieldErrors(ParameterValidationResult result) {
        if (result instanceof ParameterErrors parameterErrors) {
            Class<?> target = parameterErrors.getMethodParameter().getParameterType();
            return parameterErrors.getFieldErrors().stream()
                    .map(error -> toFieldError(target, error))
                    .toList();
        }
        String field = apiParameterName(result.getMethodParameter());
        return result.getResolvableErrors().stream()
                .map(error -> new FieldErrorDto(field, messageOf(error)))
                .toList();
    }

    /**
     * Nombre con el que el cliente conoce un parámetro: el declarado en {@link RequestParam},
     * {@link PathVariable} o {@link RequestHeader}, o el nombre Java si no se declara ninguno.
     */
    private static String apiParameterName(MethodParameter parameter) {
        String declared = "";
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        if (requestParam != null) {
            declared = requestParam.name();
        } else if (pathVariable != null) {
            declared = pathVariable.name();
        } else if (requestHeader != null) {
            declared = requestHeader.name();
        }
        if (!declared.isEmpty()) {
            return declared;
        }
        String javaName = parameter.getParameterName();
        return javaName == null ? UNKNOWN_FIELD : javaName;
    }

    private static String messageOf(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? INVALID_VALUE_MESSAGE : message;
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

    /**
     * Variante de {@link #build(HttpStatus, String, String, HttpServletRequest)} para los métodos
     * heredados de {@link ResponseEntityExceptionHandler}: delega en
     * {@link #handleExceptionInternal} para respetar sus comprobaciones (respuesta ya enviada,
     * atributo de error en los {@code 5xx}).
     */
    private ResponseEntity<Object> buildInherited(Exception ex, HttpStatus status, String title, String detail,
                                                  HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = problem(status, title, detail, requestUri(request));
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String uri) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        toInstance(uri).ifPresent(problem::setInstance);
        return problem;
    }

    /**
     * Convierte la ruta de la petición en el {@code instance} del problema. Si la ruta es nula
     * o no es una URI válida, devuelve vacío para que se omita {@code instance} sin romper el
     * manejo del error.
     */
    private static Optional<URI> toInstance(String uri) {
        if (uri == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(URI.create(uri));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String requestUri(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return null;
    }

    /**
     * Título y detalle genéricos, en español, de una respuesta de error según su estado HTTP.
     *
     * @param title  título del problema
     * @param detail detalle del problema, sin datos de la petición
     */
    private record GenericError(String title, String detail) {

        private static GenericError of(HttpStatusCode status) {
            return switch (status.value()) {
                case 400 -> new GenericError("Petición no válida", "La petición no tiene el formato esperado");
                case 404 -> new GenericError("No encontrado", "El recurso solicitado no existe");
                case 405 -> new GenericError("Método no permitido",
                        "El método HTTP no está permitido en este recurso");
                case 406 -> new GenericError("No aceptable",
                        "No se puede generar la respuesta en ninguno de los formatos aceptados");
                case 409 -> new GenericError("Conflicto",
                        "La petición entra en conflicto con el estado actual del recurso");
                case 413 -> new GenericError("Contenido demasiado grande",
                        "El cuerpo de la petición supera el tamaño máximo permitido");
                case 415 -> new GenericError("Tipo de contenido no soportado",
                        "El tipo de contenido de la petición no está soportado");
                case 503 -> new GenericError("Servicio no disponible",
                        "El servicio no está disponible temporalmente; inténtalo de nuevo más tarde");
                default -> status.is5xxServerError()
                        ? new GenericError("Error interno", "Se ha producido un error inesperado")
                        : new GenericError("Petición rechazada", "No se puede procesar la petición");
            };
        }
    }
}
