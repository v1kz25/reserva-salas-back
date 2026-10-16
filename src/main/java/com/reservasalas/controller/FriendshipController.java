package com.reservasalas.controller;

import com.reservasalas.dto.FriendDto;
import com.reservasalas.dto.FriendRequestDto;
import com.reservasalas.dto.SendFriendRequestRequest;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.service.FriendshipService;
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
 * Endpoints de amistades y solicitudes de amistad del usuario de la sesión.
 */
@RestController
@RequestMapping("/api/amistades")
public class FriendshipController {

    private final FriendshipService friendshipService;

    /**
     * Crea el controlador.
     *
     * @param friendshipService servicio de amistades
     */
    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    /**
     * Lista los amigos del usuario de la sesión.
     *
     * @param currentUser usuario de la sesión
     * @return los amigos activos, ordenados por nombre
     */
    @GetMapping
    public ResponseEntity<List<FriendDto>> findFriends(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(friendshipService.findFriends(currentUser.getId()));
    }

    /**
     * Elimina la amistad con otro usuario.
     *
     * @param userId      identificador del amigo
     * @param currentUser usuario de la sesión
     * @return respuesta vacía {@code 204}
     */
    @DeleteMapping("/{usuarioId}")
    public ResponseEntity<Void> removeFriend(@PathVariable("usuarioId") Long userId,
                                             @AuthenticationPrincipal AuthenticatedUser currentUser) {
        friendshipService.removeFriend(currentUser.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Envía una solicitud de amistad.
     *
     * @param request     destinatario de la solicitud
     * @param currentUser usuario de la sesión
     * @return la solicitud creada, con estado {@code 201}
     */
    @PostMapping("/solicitudes")
    public ResponseEntity<FriendRequestDto> sendRequest(@Valid @RequestBody SendFriendRequestRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser currentUser) {
        FriendRequestDto created = friendshipService.sendRequest(currentUser.getId(), request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lista las solicitudes pendientes recibidas.
     *
     * @param currentUser usuario de la sesión
     * @return las solicitudes, las más recientes primero
     */
    @GetMapping("/solicitudes/recibidas")
    public ResponseEntity<List<FriendRequestDto>> findReceived(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(friendshipService.findReceived(currentUser.getId()));
    }

    /**
     * Lista las solicitudes pendientes enviadas.
     *
     * @param currentUser usuario de la sesión
     * @return las solicitudes, las más recientes primero
     */
    @GetMapping("/solicitudes/enviadas")
    public ResponseEntity<List<FriendRequestDto>> findSent(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(friendshipService.findSent(currentUser.getId()));
    }

    /**
     * Retira una solicitud pendiente enviada.
     *
     * @param id          identificador de la solicitud
     * @param currentUser usuario de la sesión
     * @return respuesta vacía {@code 204}
     */
    @DeleteMapping("/solicitudes/{id}")
    public ResponseEntity<Void> withdrawRequest(@PathVariable Long id,
                                                @AuthenticationPrincipal AuthenticatedUser currentUser) {
        friendshipService.withdrawRequest(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Acepta una solicitud pendiente recibida.
     *
     * @param id          identificador de la solicitud
     * @param currentUser usuario de la sesión
     * @return respuesta vacía {@code 204}
     */
    @PostMapping("/solicitudes/{id}/aceptar")
    public ResponseEntity<Void> acceptRequest(@PathVariable Long id,
                                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        friendshipService.acceptRequest(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Rechaza una solicitud pendiente recibida; la solicitud se elimina.
     *
     * @param id          identificador de la solicitud
     * @param currentUser usuario de la sesión
     * @return respuesta vacía {@code 204}
     */
    @PostMapping("/solicitudes/{id}/rechazar")
    public ResponseEntity<Void> rejectRequest(@PathVariable Long id,
                                              @AuthenticationPrincipal AuthenticatedUser currentUser) {
        friendshipService.rejectRequest(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
