package com.reservasalas.dto;

import com.reservasalas.exception.InvalidReservationFilterException;
import java.util.Arrays;

/**
 * Papel del usuario de la sesión en una reserva, para filtrar «Mis reservas» (parámetro {@code rol}
 * del contrato).
 */
public enum MyReservationRole {

    /** Reservas que organiza el usuario. */
    ORGANIZED("ORGANIZADA"),

    /** Reservas en las que el usuario tiene la invitación aceptada. */
    INVITED("INVITADO"),

    /** Reservas en las que el usuario tiene la invitación pendiente, también las ya pasadas. */
    PENDING("PENDIENTE");

    private final String apiValue;

    MyReservationRole(String apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * Convierte el valor recibido en la API en el papel correspondiente. Se distinguen mayúsculas y
     * minúsculas, como en el resto de enumerados del contrato.
     *
     * @param value valor del parámetro {@code rol}; puede ser nulo
     * @return el papel, o {@code null} si no se ha indicado
     * @throws InvalidReservationFilterException si el valor no es uno de los del contrato
     */
    public static MyReservationRole fromApiValue(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(role -> role.apiValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new InvalidReservationFilterException("rol",
                        "debe ser ORGANIZADA, INVITADO o PENDIENTE"));
    }

    /**
     * Valor del papel en la API.
     *
     * @return el valor en español
     */
    public String apiValue() {
        return apiValue;
    }
}
