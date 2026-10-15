package com.reservasalas.repository;

/**
 * Estado de un usuario que decide si sus sesiones abiertas siguen siendo válidas.
 *
 * @param active         {@code true} si el usuario está activo
 * @param sessionVersion versión de sesión actual del usuario
 */
public record UserSessionState(boolean active, long sessionVersion) {
}
