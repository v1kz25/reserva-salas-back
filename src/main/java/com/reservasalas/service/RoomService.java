package com.reservasalas.service;

import com.reservasalas.dto.RoomDto;
import com.reservasalas.repository.RoomRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de salas.
 */
@Service
public class RoomService {

    private final RoomRepository roomRepository;

    /**
     * Crea el servicio.
     *
     * @param roomRepository repositorio de salas
     */
    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * Lista todas las salas ordenadas por nombre.
     *
     * @return las salas, vacía si no hay ninguna
     */
    @Transactional(readOnly = true)
    public List<RoomDto> findAll() {
        return roomRepository.findAllByOrderByNameAsc().stream()
                .map(RoomDto::from)
                .toList();
    }
}
