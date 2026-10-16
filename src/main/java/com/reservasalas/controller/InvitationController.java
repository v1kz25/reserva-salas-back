package com.reservasalas.controller;

import com.reservasalas.dto.InvitationDto;
import com.reservasalas.dto.InvitationRequest;
import com.reservasalas.dto.ReceivedInvitationDto;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.service.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de las invitaciones a reservas: el organizador invita a sus amigos y retira
 * invitaciones; el invitado consulta sus invitaciones pendientes y las acepta o rechaza.
 */
@RestController
@RequestMapping("/api")
public class InvitationController {

    private final InvitationService invitationService;

    /**
     * Crea el controlador.
     *
     * @param invitationService servicio de invitaciones
     */
    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    /**
     * Invita a un amigo a una reserva del usuario de la sesión.
     *
     * @param id          identificador de la reserva
     * @param request     usuario al que se invita
     * @param currentUser usuario de la sesión, que debe ser el organizador
     * @return la invitación creada, con {@code 201}
     */
    @PostMapping("/reservas/{id}/invitaciones")
    public ResponseEntity<InvitationDto> invite(@PathVariable Long id,
                                                @Valid @RequestBody InvitationRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        InvitationDto created = invitationService.invite(id, request.userId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Retira una invitación de una reserva del usuario de la sesión.
     *
     * @param id           identificador de la reserva
     * @param invitationId identificador de la invitación
     * @param currentUser  usuario de la sesión, que debe ser el organizador
     * @return respuesta vacía {@code 204}
     */
    @DeleteMapping("/reservas/{id}/invitaciones/{invitacionId}")
    public ResponseEntity<Void> withdraw(@PathVariable Long id,
                                         @PathVariable("invitacionId") Long invitationId,
                                         @AuthenticationPrincipal AuthenticatedUser currentUser) {
        invitationService.withdraw(id, invitationId, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lista las invitaciones pendientes del usuario de la sesión a reservas que aún no han pasado.
     *
     * @param currentUser usuario de la sesión
     * @return las invitaciones, ordenadas por fecha y hora de la reserva
     */
    @GetMapping("/invitaciones/pendientes")
    public ResponseEntity<List<ReceivedInvitationDto>> findPending(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(invitationService.findPending(currentUser.getId()));
    }

    /**
     * Acepta una invitación pendiente del usuario de la sesión.
     *
     * @param id          identificador de la invitación
     * @param currentUser usuario de la sesión, que debe ser el invitado
     * @return respuesta vacía {@code 204}
     */
    @PostMapping("/invitaciones/{id}/aceptar")
    public ResponseEntity<Void> accept(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser currentUser) {
        invitationService.accept(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Rechaza una invitación pendiente o aceptada del usuario de la sesión.
     *
     * @param id          identificador de la invitación
     * @param currentUser usuario de la sesión, que debe ser el invitado
     * @return respuesta vacía {@code 204}
     */
    @PostMapping("/invitaciones/{id}/rechazar")
    public ResponseEntity<Void> reject(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser currentUser) {
        invitationService.reject(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
