package com.reservasalas.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límite de intentos fallidos de inicio de sesión por email e IP del cliente. Si algún valor no es
 * válido, la aplicación no arranca.
 *
 * @param maxAttempts   intentos fallidos permitidos dentro de la ventana antes de bloquear
 *                      ({@code LOGIN_MAX_INTENTOS})
 * @param windowMinutes duración de la ventana en minutos, contada desde el primer fallo
 *                      ({@code LOGIN_VENTANA_MINUTOS})
 * @param maxEntries    número máximo de combinaciones email e IP que se guardan en memoria
 *                      ({@code LOGIN_MAX_ENTRADAS})
 */
@Validated
@ConfigurationProperties(prefix = "app.login")
public record LoginAttemptProperties(
        @Min(1) @Max(1000) int maxAttempts,
        @Min(1) @Max(1440) int windowMinutes,
        @Min(1) @Max(1_000_000) int maxEntries) {
}
