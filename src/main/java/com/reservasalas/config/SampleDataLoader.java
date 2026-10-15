package com.reservasalas.config;

import com.reservasalas.entity.Room;
import com.reservasalas.repository.RoomRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga salas de ejemplo al arrancar. Solo se activa con el perfil {@code dev}.
 */
@Component
@Profile("dev")
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SampleDataLoader.class);

    private final RoomRepository roomRepository;

    /**
     * Crea el cargador.
     *
     * @param roomRepository repositorio de salas
     */
    public SampleDataLoader(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * Inserta las salas de ejemplo si la base de datos no tiene ninguna.
     *
     * @param args argumentos de arranque (no se usan)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (roomRepository.count() > 0) {
            return;
        }
        List<Room> rooms = roomRepository.saveAll(List.of(
                new Room("Sala Atlántico", 8, 2),
                new Room("Sala Cantábrico", 12, 1),
                new Room("Sala Mediterráneo", 20, 0),
                new Room("Sala Sótano", 4, -1)));
        LOG.info("Cargadas {} salas de ejemplo", rooms.size());
    }
}
