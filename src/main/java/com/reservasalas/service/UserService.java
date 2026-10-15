package com.reservasalas.service;

import com.reservasalas.dto.CreateUserRequest;
import com.reservasalas.dto.PasswordRequest;
import com.reservasalas.dto.UpdateUserRequest;
import com.reservasalas.dto.UserAdminDto;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.exception.DuplicateEmailException;
import com.reservasalas.exception.PasswordTooLongException;
import com.reservasalas.exception.SelfDemotionException;
import com.reservasalas.exception.UserNotFoundException;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de usuarios por parte de un administrador: consulta, alta, cambio de nombre, rol y estado
 * y cambio de contraseña. Las contraseñas se guardan en BCrypt. Al cambiar el rol, el estado o la
 * contraseña de un usuario se incrementa su versión de sesión, lo que cierra sus sesiones abiertas.
 * Nunca se registran en el log emails ni contraseñas.
 */
@Service
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";
    private static final String UNIQUE_EMAIL_CONSTRAINT = User.UNIQUE_EMAIL_CONSTRAINT.toLowerCase(Locale.ROOT);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * Crea el servicio.
     *
     * @param userRepository  repositorio de usuarios
     * @param passwordEncoder codificador de contraseñas
     * @param clock           reloj con el que se fecha el alta
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Lista todos los usuarios ordenados por nombre.
     *
     * @return los usuarios, vacía si no hay ninguno
     */
    @Transactional(readOnly = true)
    public List<UserAdminDto> findAll() {
        return userRepository.findAllByOrderByNameAscIdAsc().stream()
                .map(UserAdminDto::from)
                .toList();
    }

    /**
     * Obtiene un usuario por su identificador.
     *
     * @param id identificador del usuario
     * @return el usuario
     * @throws UserNotFoundException si el usuario no existe
     */
    @Transactional(readOnly = true)
    public UserAdminDto findById(Long id) {
        return userRepository.findById(id)
                .map(UserAdminDto::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Crea un usuario activo.
     *
     * @param request datos del usuario, con el email ya normalizado
     * @return el usuario creado
     * @throws PasswordTooLongException si la contraseña supera los
     *                                  {@value UserAccountService#MAX_PASSWORD_BYTES} bytes en UTF-8
     * @throws DuplicateEmailException  si ya existe un usuario con ese email, también si otra alta
     *                                  simultánea se adelanta
     */
    @Transactional
    public UserAdminDto create(CreateUserRequest request) {
        String hash = encode(request.password());
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        User user = new User(request.email(), request.name(), hash, request.role(), createdAt);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e);
        }
        LOG.info("Usuario {} creado con rol {}", user.getId(), user.getRole());
        return UserAdminDto.from(user);
    }

    /**
     * Cambia el nombre, el rol y el estado de un usuario. Si cambian el rol o el estado, sus
     * sesiones abiertas se cierran.
     *
     * @param id      identificador del usuario
     * @param request nuevos datos
     * @param actor   administrador que hace el cambio
     * @return el usuario modificado
     * @throws UserNotFoundException si el usuario no existe
     * @throws SelfDemotionException si el administrador intenta desactivarse o quitarse el rol de
     *                               administrador a sí mismo
     */
    @Transactional
    public UserAdminDto update(Long id, UpdateUserRequest request, AuthenticatedUser actor) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        boolean active = request.active();
        if (id.equals(actor.getId()) && (!active || request.role() != Role.ADMIN)) {
            throw new SelfDemotionException();
        }
        long previousVersion = user.getSessionVersion();
        user.update(request.name(), request.role(), active);
        userRepository.flush();
        if (user.getSessionVersion() != previousVersion) {
            LOG.info("Usuario {} modificado (rol {}, activo {}); se cierran sus sesiones", id, user.getRole(),
                    user.isActive());
        } else {
            LOG.info("Usuario {} modificado", id);
        }
        return UserAdminDto.from(user);
    }

    /**
     * Cambia la contraseña de un usuario y cierra sus sesiones abiertas.
     *
     * @param id      identificador del usuario
     * @param request nueva contraseña
     * @throws UserNotFoundException    si el usuario no existe
     * @throws PasswordTooLongException si la contraseña supera los
     *                                  {@value UserAccountService#MAX_PASSWORD_BYTES} bytes en UTF-8
     */
    @Transactional
    public void changePassword(Long id, PasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.changePasswordHash(encode(request.password()));
        userRepository.flush();
        LOG.info("Contraseña del usuario {} cambiada; se cierran sus sesiones", id);
    }

    /**
     * Comprueba el límite de bytes de BCrypt y codifica la contraseña.
     */
    private String encode(String rawPassword) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > UserAccountService.MAX_PASSWORD_BYTES) {
            throw new PasswordTooLongException(UserAccountService.MAX_PASSWORD_BYTES);
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Traduce la violación de la restricción única del email a {@link DuplicateEmailException};
     * cualquier otra violación de integridad se devuelve tal cual para que acabe como error interno.
     */
    private static RuntimeException translateIntegrityViolation(DataIntegrityViolationException e) {
        if (violatesUniqueEmail(e)) {
            LOG.warn("Email de usuario repetido detectado por la base de datos");
            return new DuplicateEmailException();
        }
        return e;
    }

    /**
     * Decide si la violación de integridad se debe a la restricción única del email. Si alguna causa
     * informa del nombre de la restricción, manda ese nombre; si no, basta con que la base de datos
     * indique una violación de unicidad (SQLSTATE {@code 23505}), porque la única restricción única
     * de los usuarios, aparte de la clave primaria generada, es la del email.
     */
    private static boolean violatesUniqueEmail(Throwable error) {
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
            return constraintName.toLowerCase(Locale.ROOT).contains(UNIQUE_EMAIL_CONSTRAINT);
        }
        return uniqueViolation;
    }
}
