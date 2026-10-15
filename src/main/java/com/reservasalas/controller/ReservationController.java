package com.reservasalas.controller;

import com.reservasalas.dto.CreateReservationRequest;
import com.reservasalas.dto.ReservationDto;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.service.ReservationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints de consulta, alta y cancelación de reservas.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * Crea el controlador.
     *
     * @param reservationService servicio de reservas
     */
    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * Lista las reservas ordenadas por fecha y hora de inicio.
     *
     * @param roomId filtro opcional por sala
     * @param from   filtro opcional: primer día incluido
     * @param to     filtro opcional: último día incluido
     * @return las reservas que cumplen los filtros
     */
    @GetMapping
    public ResponseEntity<List<ReservationDto>> find(@RequestParam(name = "salaId", required = false) Long roomId,
                                                     @RequestParam(name = "fechaDesde", required = false) LocalDate from,
                                                     @RequestParam(name = "fechaHasta", required = false) LocalDate to) {
        return ResponseEntity.ok(reservationService.find(roomId, from, to));
    }

    /**
     * Obtiene una reserva.
     *
     * @param id identificador de la reserva
     * @return la reserva
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.findById(id));
    }

    /**
     * Crea una reserva a nombre del usuario de la sesión.
     *
     * @param request     datos de la reserva
     * @param currentUser usuario de la sesión
     * @return la reserva creada, con la cabecera {@code Location} apuntando a ella
     */
    @PostMapping
    public ResponseEntity<ReservationDto> create(@Valid @RequestBody CreateReservationRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ReservationDto created = reservationService.create(request, currentUser);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Cancela una reserva; su franja queda libre. Un usuario solo cancela las suyas; un
     * administrador, cualquiera.
     *
     * @param id          identificador de la reserva
     * @param currentUser usuario de la sesión
     * @return respuesta vacía {@code 204}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser currentUser) {
        reservationService.cancel(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
