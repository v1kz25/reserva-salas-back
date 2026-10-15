package com.reservasalas.service;

import com.reservasalas.dto.RoomDto;
import com.reservasalas.dto.RoomRequest;
import com.reservasalas.entity.Room;
import com.reservasalas.exception.DuplicateRoomNameException;
import com.reservasalas.exception.RoomHasFutureReservationsException;
import com.reservasalas.exception.RoomNotFoundException;
import com.reservasalas.repository.ReservationRepository;
import com.reservasalas.repository.RoomRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de las salas: consulta, alta, modificación y baja. El nombre de una sala es
 * único sin distinguir mayúsculas ni espacios de los extremos.
 */
@Service
public class RoomService {

    private static final Logger LOG = LoggerFactory.getLogger(RoomService.class);
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String UNIQUE_NAME_CONSTRAINT = Room.UNIQUE_NAME_CONSTRAINT.toLowerCase(Locale.ROOT);

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    /**
     * Crea el servicio.
     *
     * @param roomRepository        repositorio de salas
     * @param reservationRepository repositorio de reservas
     * @param clock                 reloj con el que se decide qué día es hoy
     */
    public RoomService(RoomRepository roomRepository, ReservationRepository reservationRepository, Clock clock) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
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

    /**
     * Obtiene una sala por su identificador.
     *
     * @param id identificador de la sala
     * @return la sala
     * @throws RoomNotFoundException si la sala no existe
     */
    @Transactional(readOnly = true)
    public RoomDto findById(Long id) {
        return roomRepository.findById(id)
                .map(RoomDto::from)
                .orElseThrow(() -> new RoomNotFoundException(id));
    }

    /**
     * Crea una sala.
     *
     * @param request datos de la sala
     * @return la sala creada
     * @throws DuplicateRoomNameException si ya existe una sala con ese nombre, también si otra alta
     *                                    simultánea se adelanta
     */
    @Transactional
    public RoomDto create(RoomRequest request) {
        if (roomRepository.existsByNameKey(Room.normalizeName(request.name()))) {
            throw new DuplicateRoomNameException();
        }
        Room room = new Room(request.name(), request.capacity(), request.floor());
        try {
            roomRepository.saveAndFlush(room);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e);
        }
        LOG.info("Sala {} creada", room.getId());
        return RoomDto.from(room);
    }

    /**
     * Modifica los datos de una sala. La sala se bloquea durante la transacción para serializarse
     * con su eliminación y con el alta de reservas.
     *
     * @param id      identificador de la sala
     * @param request nuevos datos de la sala
     * @return la sala modificada
     * @throws RoomNotFoundException      si la sala no existe
     * @throws DuplicateRoomNameException si otra sala ya usa ese nombre, también si otro cambio
     *                                    simultáneo se adelanta
     */
    @Transactional
    public RoomDto update(Long id, RoomRequest request) {
        Room room = roomRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RoomNotFoundException(id));
        if (roomRepository.existsByNameKeyAndIdNot(Room.normalizeName(request.name()), id)) {
            throw new DuplicateRoomNameException();
        }
        room.update(request.name(), request.capacity(), request.floor());
        try {
            roomRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e);
        }
        LOG.info("Sala {} modificada", id);
        return RoomDto.from(room);
    }

    /**
     * Elimina una sala junto con sus reservas pasadas. No se permite si tiene reservas de hoy o
     * posteriores. La sala se bloquea durante la transacción para que no pueda entrar una reserva
     * nueva mientras se elimina (el alta de reservas bloquea la misma fila).
     *
     * @param id identificador de la sala
     * @throws RoomNotFoundException              si la sala no existe
     * @throws RoomHasFutureReservationsException si la sala tiene reservas de hoy o posteriores
     */
    @Transactional
    public void delete(Long id) {
        roomRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RoomNotFoundException(id));
        if (reservationRepository.existsByRoomFrom(id, LocalDate.now(clock))) {
            throw new RoomHasFutureReservationsException(id);
        }
        int pastReservations = reservationRepository.deleteByRoomId(id);
        roomRepository.deleteById(id);
        LOG.info("Sala {} eliminada junto con {} reservas pasadas", id, pastReservations);
    }

    /**
     * Traduce la violación de la restricción única del nombre a {@link DuplicateRoomNameException};
     * cualquier otra violación de integridad se devuelve tal cual para que acabe como error interno.
     */
    private static RuntimeException translateIntegrityViolation(DataIntegrityViolationException e) {
        if (violatesUniqueName(e)) {
            LOG.warn("Nombre de sala repetido detectado por la base de datos");
            return new DuplicateRoomNameException();
        }
        return e;
    }

    /**
     * Decide si la violación de integridad se debe a la restricción única del nombre normalizado.
     * Si alguna causa informa del nombre de la restricción, manda ese nombre; si no, basta con que
     * la base de datos indique una violación de unicidad (SQLSTATE {@code 23505}), porque la única
     * restricción única de las salas, aparte de la clave primaria generada, es la del nombre.
     */
    private static boolean violatesUniqueName(Throwable error) {
        String constraintName = null;
        boolean uniqueViolation = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                constraintName = violation.getConstraintName();
            }
            if (cause instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                uniqueViolation = true;
            }
        }
        if (constraintName != null) {
            return constraintName.toLowerCase(Locale.ROOT).contains(UNIQUE_NAME_CONSTRAINT);
        }
        return uniqueViolation;
    }
}
