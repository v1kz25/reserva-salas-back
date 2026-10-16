package com.reservasalas.controller;

import com.reservasalas.dto.CreateReservationRequest;
import com.reservasalas.dto.PageDto;
import com.reservasalas.dto.ReservationDto;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
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
     * Lista una página de las reservas, ordenadas por fecha y hora de inicio. Las reservas en las que
     * el usuario no participa salen solo como franja ocupada.
     *
     * @param mine        si es {@code true}, solo «Mis reservas»: las que organiza el usuario y
     *                    aquellas en las que tiene una invitación pendiente o aceptada
     * @param roomId      filtro opcional por sala
     * @param from        filtro opcional: primer día incluido
     * @param to          filtro opcional: último día incluido
     * @param page        número de página, empezando en 0
     * @param size        tamaño de página, de 1 a {@value PageDto#MAX_SIZE}
     * @param currentUser usuario de la sesión
     * @return la página de reservas que cumplen los filtros
     */
    @GetMapping
    public ResponseEntity<PageDto<ReservationDto>> find(
            @RequestParam(name = "mis", defaultValue = "false") boolean mine,
            @RequestParam(name = "salaId", required = false) Long roomId,
            @RequestParam(name = "fechaDesde", required = false) LocalDate from,
            @RequestParam(name = "fechaHasta", required = false) LocalDate to,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "debe ser al menos 0") int page,
            @RequestParam(name = "size", defaultValue = PageDto.DEFAULT_SIZE)
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = PageDto.MAX_SIZE, message = "no puede ser mayor que " + PageDto.MAX_SIZE) int size,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(reservationService.find(roomId, from, to, mine, page, size, currentUser));
    }

    /**
     * Obtiene una reserva: completa y con sus invitaciones si el usuario participa en ella o es
     * administrador; si no, solo la franja ocupada.
     *
     * @param id          identificador de la reserva
     * @param currentUser usuario de la sesión
     * @return la reserva
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReservationDto> findById(@PathVariable Long id,
                                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(reservationService.findById(id, currentUser));
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
