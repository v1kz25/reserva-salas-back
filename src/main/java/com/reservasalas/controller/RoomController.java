package com.reservasalas.controller;

import com.reservasalas.dto.RoomDto;
import com.reservasalas.service.RoomService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de consulta de salas.
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
}
