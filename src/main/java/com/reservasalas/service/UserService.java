package com.reservasalas.service;

import com.reservasalas.dto.CreateUserRequest;
import com.reservasalas.dto.PasswordRequest;
import com.reservasalas.dto.UpdateUserRequest;
import com.reservasalas.dto.UserAdminDto;
import com.reservasalas.entity.Role;
import com.reservasalas.entity.User;
import com.reservasalas.exception.DuplicateEmailException;
import com.reservasalas.exception.LastActiveAdminException;
import com.reservasalas.exception.PasswordTooLongException;
import com.reservasalas.exception.SelfDemotionException;
import com.reservasalas.exception.UniqueConstraintViolations;
import com.reservasalas.exception.UserNotFoundException;
import com.reservasalas.repository.UserRepository;
import com.reservasalas.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gestión de usuarios por parte de un administrador: consulta, alta, cambio de nombre, rol y estado
 * y cambio de contraseña. Las contraseñas se guardan en BCrypt. Al cambiar el rol, el estado o la
 * contraseña de un usuario se incrementa su versión de sesión, lo que cierra sus sesiones abiertas.
 * Ningún cambio puede dejar la aplicación sin al menos un administrador activo. El hash BCrypt, que
 * es lento a propósito, se calcula antes de abrir la transacción. Nunca se registran en el log
 * emails ni contraseñas.
 */
@Service
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /**
     * Crea el servicio.
     *
     * @param userRepository     repositorio de usuarios
     * @param passwordEncoder    codificador de contraseñas
     * @param clock              reloj con el que se fecha el alta
     * @param transactionManager gestor de transacciones, para abrir la transacción después de
     *                           calcular el hash
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock,
                       PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
     * Crea un usuario activo. El hash de la contraseña se calcula fuera de la transacción.
     *
     * @param request datos del usuario, con el email ya normalizado
     * @return el usuario creado
     * @throws PasswordTooLongException si la contraseña supera los
     *                                  {@value UserAccountService#MAX_PASSWORD_BYTES} bytes en UTF-8
     * @throws DuplicateEmailException  si ya existe un usuario con ese email, también si otra alta
     *                                  simultánea se adelanta
     */
    public UserAdminDto create(CreateUserRequest request) {
        String hash = encode(request.password());
        return Objects.requireNonNull(transactionTemplate.execute(status -> insert(request, hash)));
    }

    /**
     * Cambia el nombre, el rol y el estado de un usuario. Si cambian el rol o el estado, sus
     * sesiones abiertas se cierran. Si el cambio desactiva o degrada a un administrador activo, se
     * bloquean antes los administradores activos para que dos cambios simultáneos no dejen la
     * aplicación sin ninguno.
     *
     * @param id      identificador del usuario
     * @param request nuevos datos
     * @param actor   administrador que hace el cambio
     * @return el usuario modificado
     * @throws UserNotFoundException    si el usuario no existe
     * @throws SelfDemotionException    si el administrador intenta desactivarse o quitarse el rol de
     *                                  administrador a sí mismo
     * @throws LastActiveAdminException si el cambio dejaría la aplicación sin administradores activos
     * @throws org.springframework.dao.OptimisticLockingFailureException si otro cambio simultáneo
     *                                  sobre el mismo usuario se adelanta
     */
    @Transactional
    public UserAdminDto update(Long id, UpdateUserRequest request, AuthenticatedUser actor) {
        boolean active = request.active();
        boolean removesAdmin = !active || request.role() != Role.ADMIN;
        if (removesAdmin && id.equals(actor.getId())) {
            throw new SelfDemotionException();
        }
        List<User> activeAdmins = removesAdmin ? userRepository.findAllByRoleAndActiveTrueOrderByIdAsc(Role.ADMIN) : List.of();
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (removesAdmin && user.isActiveAdmin()
                && activeAdmins.stream().allMatch(admin -> admin.getId().equals(id))) {
            throw new LastActiveAdminException();
        }
        boolean sessionsMustClose = user.update(request.name(), request.role(), active);
        userRepository.flush();
        if (sessionsMustClose) {
            userRepository.incrementSessionVersion(id);
            LOG.info("Usuario {} modificado (rol {}, activo {}); se cierran sus sesiones", id, user.getRole(),
                    user.isActive());
        } else {
            LOG.info("Usuario {} modificado", id);
        }
        return UserAdminDto.from(user);
    }

    /**
     * Cambia la contraseña de un usuario y cierra sus sesiones abiertas. El hash se calcula fuera
     * de la transacción.
     *
     * @param id      identificador del usuario
     * @param request nueva contraseña
     * @throws UserNotFoundException    si el usuario no existe
     * @throws PasswordTooLongException si la contraseña supera los
     *                                  {@value UserAccountService#MAX_PASSWORD_BYTES} bytes en UTF-8
     * @throws org.springframework.dao.OptimisticLockingFailureException si otro cambio simultáneo
     *                                  sobre el mismo usuario se adelanta
     */
    public void changePassword(Long id, PasswordRequest request) {
        String hash = encode(request.password());
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));
            user.changePasswordHash(hash);
            userRepository.flush();
            userRepository.incrementSessionVersion(id);
        });
        LOG.info("Contraseña del usuario {} cambiada; se cierran sus sesiones", id);
    }

    /**
     * Inserta el usuario dentro de la transacción en curso, con el hash ya calculado.
     */
    private UserAdminDto insert(CreateUserRequest request, String hash) {
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
        if (UniqueConstraintViolations.isViolation(e, User.UNIQUE_EMAIL_CONSTRAINT)) {
            LOG.warn("Email de usuario repetido detectado por la base de datos");
            return new DuplicateEmailException();
        }
        return e;
    }
}
