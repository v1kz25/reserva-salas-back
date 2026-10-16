package com.reservasalas.dto;

import java.time.LocalDate;

/**
 * Filtros opcionales del listado de reservas.
 *
 * @param roomId identificador de la sala; si es nulo, no filtra por sala
 * @param from   primer día incluido; si es nulo, no hay límite inferior
 * @param to     último día incluido; si es nulo, no hay límite superior
 * @param mine   si es {@code true}, solo «Mis reservas»: las que organiza el usuario y aquellas en
 *               las que tiene una invitación pendiente o aceptada
 * @param role   con {@code mine}, papel del usuario en la reserva; si es nulo, cualquiera. Sin
 *               {@code mine} se ignora
 */
public record ReservationFilter(Long roomId, LocalDate from, LocalDate to, boolean mine,
                                MyReservationRole role) {
}
