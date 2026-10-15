package com.reservasalas.controller;

import com.reservasalas.dto.RoomDto;
import com.reservasalas.dto.RoomRequest;
import com.reservasalas.service.RoomService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Endpoints de consulta y gestión de salas. Crear, modificar y eliminar salas es solo para
 * administradores; la restricción se aplica en {@link com.reservasalas.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/salas")
public class RoomController {

    private final RoomService roomService;

    /**
     * Crea el controlador.
     *
     * @param roomService servicio de salas
     */
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * Lista todas las salas ordenadas por nombre.
     *
     * @return las salas
     */
    @GetMapping
    public ResponseEntity<List<RoomDto>> findAll() {
        return ResponseEntity.ok(roomService.findAll());
    }

    /**
     * Obtiene una sala.
     *
     * @param id identificador de la sala
     * @return la sala
     */
    @GetMapping("/{id}")
    public ResponseEntity<RoomDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(roomService.findById(id));
    }

    /**
     * Crea una sala.
     *
     * @param request datos de la sala
     * @return la sala creada, con la cabecera {@code Location} apuntando a ella
     */
    @PostMapping
    public ResponseEntity<RoomDto> create(@Valid @RequestBody RoomRequest request) {
        RoomDto created = roomService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Modifica una sala.
     *
     * @param id      identificador de la sala
     * @param request nuevos datos de la sala
     * @return la sala modificada
     */
    @PutMapping("/{id}")
    public ResponseEntity<RoomDto> update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return ResponseEntity.ok(roomService.update(id, request));
    }

    /**
     * Elimina una sala y sus reservas pasadas. No se permite si tiene reservas de hoy o posteriores.
     *
     * @param id identificador de la sala
     * @return respuesta vacía {@code 204}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
