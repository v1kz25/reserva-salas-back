package com.reservasalas.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Responde a los errores de seguridad de los filtros (sin sesión: {@code 401}; sin permiso o sin
 * token CSRF válido: {@code 403}) delegando en el manejador global de excepciones, para que salgan
 * como {@code ProblemDetail} con el mismo formato que el resto de errores. No añade la cabecera
 * {@code WWW-Authenticate}, así que el navegador no muestra su diálogo de credenciales.
 */
public final class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver exceptionResolver;

    /**
     * Crea el manejador.
     *
     * @param exceptionResolver resolutor de excepciones de Spring MVC, que aplica el
     *                          {@code @RestControllerAdvice} global
     */
    public ProblemDetailSecurityHandler(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        resolve(request, response, authException, HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        resolve(request, response, accessDeniedException, HttpServletResponse.SC_FORBIDDEN);
    }

    /**
     * Delega en el manejador global; si por cualquier motivo no resuelve la excepción, responde al
     * menos con el código de estado adecuado.
     */
    private void resolve(HttpServletRequest request, HttpServletResponse response, RuntimeException ex,
                         int fallbackStatus) throws IOException {
        if (exceptionResolver.resolveException(request, response, null, ex) == null) {
            response.sendError(fallbackStatus);
        }
    }
}
