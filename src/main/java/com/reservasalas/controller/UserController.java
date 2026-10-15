package com.reservasalas.controller;

import com.reservasalas.dto.CreateUserRequest;
import com.reservasalas.dto.PasswordRequest;
import com.reservasalas.dto.UpdateUserRequest;
import com.reservasalas.dto.UserAdminDto;
import com.reservasalas.security.AuthenticatedUser;
import com.reservasalas.security.CurrentSessionUpdater;
import com.reservasalas.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints de gestión de usuarios, solo para administradores; la restricción se aplica en
 * {@link com.reservasalas.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UserController {

    private final UserService userService;
    private final CurrentSessionUpdater currentSessionUpdater;

    /**
     * Crea el controlador.
     *
     * @param userService           servicio de usuarios
     * @param currentSessionUpdater actualiza la sesión propia cuando el administrador se modifica
     *                              a sí mismo
     */
    public UserController(UserService userService, CurrentSessionUpdater currentSessionUpdater) {
        this.userService = userService;
        this.currentSessionUpdater = currentSessionUpdater;
    }

    /**
     * Lista los usuarios ordenados por nombre.
     *
     * @return los usuarios
     */
    @GetMapping
    public ResponseEntity<List<UserAdminDto>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    /**
     * Obtiene un usuario.
     *
     * @param id identificador del usuario
     * @return el usuario
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserAdminDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /**
     * Crea un usuario.
     *
     * @param request datos del usuario
     * @return el usuario creado, con la cabecera {@code Location} apuntando a él
     */
    @PostMapping
    public ResponseEntity<UserAdminDto> create(@Valid @RequestBody CreateUserRequest request) {
        UserAdminDto created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Cambia el nombre, el rol y el estado de un usuario. Si cambian el rol o el estado, se cierran
     * sus sesiones.
     *
     * @param id       identificador del usuario
     * @param request  nuevos datos
     * @param actor    administrador de la sesión
     * @param http     petición en curso
     * @param response respuesta en curso
     * @return el usuario modificado
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserAdminDto> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateUserRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser actor,
                                               HttpServletRequest http,
                                               HttpServletResponse response) {
        UserAdminDto updated = userService.update(id, request, actor);
        currentSessionUpdater.refreshIfCurrent(id, actor, http, response);
        return ResponseEntity.ok(updated);
    }

    /**
     * Cambia la contraseña de un usuario y cierra sus sesiones. Si es la del propio administrador,
     * su sesión actual sigue abierta.
     *
     * @param id       identificador del usuario
     * @param request  nueva contraseña
     * @param actor    administrador de la sesión
     * @param http     petición en curso
     * @param response respuesta en curso
     * @return respuesta vacía {@code 204}
     */
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(@PathVariable Long id,
                                               @Valid @RequestBody PasswordRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser actor,
                                               HttpServletRequest http,
                                               HttpServletResponse response) {
        userService.changePassword(id, request);
        currentSessionUpdater.refreshIfCurrent(id, actor, http, response);
        return ResponseEntity.noContent().build();
    }
}
