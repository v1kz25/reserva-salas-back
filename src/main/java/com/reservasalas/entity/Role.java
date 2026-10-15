package com.reservasalas.entity;

/**
 * Rol de un usuario de la aplicación, que determina qué operaciones puede hacer.
 */
public enum Role {

    /** Usuario normal: reserva salas y cancela sus propias reservas. */
    USER,

    /** Administrador: gestiona salas y puede cancelar cualquier reserva. */
    ADMIN;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    /**
     * Nombre de la autoridad de Spring Security asociada al rol.
     *
     * @return la autoridad, p. ej. {@code ROLE_ADMIN}
     */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
