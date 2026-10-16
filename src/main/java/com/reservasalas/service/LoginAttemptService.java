package com.reservasalas.service;

import com.reservasalas.config.LoginAttemptProperties;
import com.reservasalas.entity.User;
import com.reservasalas.exception.TooManyLoginAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Límite de intentos de inicio de sesión por email normalizado e IP del cliente, en memoria de cada
 * instancia.
 *
 * <p>La ventana es fija y empieza con el primer intento de la serie: dentro de ella se permiten
 * {@code maxAttempts} comprobaciones de credenciales; las siguientes se rechazan sin comprobarlas
 * hasta que la ventana termina. El intento se anota antes de comprobar las credenciales, de forma
 * que varias peticiones simultáneas no pueden superar el límite; un login correcto borra la serie.
 *
 * <p>El registro no supera {@code maxEntries} series. Las caducadas se borran cada minuto. Si al dar
 * de alta una serie el registro está lleno, se expulsa una caducada; si no hay ninguna, la no
 * bloqueada más antigua; y si todas están bloqueadas, la serie nueva no se registra y ese intento
 * se procesa sin contar. Así, llenar el registro con emails inventados nunca desbloquea una serie
 * bloqueada. El alta y la expulsión se hacen bajo el mismo cerrojo para que el tope no se supere con
 * peticiones simultáneas.
 */
@Service
@EnableConfigurationProperties(LoginAttemptProperties.class)
public class LoginAttemptService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final char KEY_SEPARATOR = '\n';

    private final Clock clock;
    private final int maxAttempts;
    private final Duration window;
    private final int maxEntries;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final ReentrantLock registrationLock = new ReentrantLock();

    /**
     * Crea el servicio.
     *
     * @param clock      reloj de la aplicación
     * @param properties límite de intentos, ventana y tamaño máximo del registro
     */
    public LoginAttemptService(Clock clock, LoginAttemptProperties properties) {
        this.clock = clock;
        this.maxAttempts = properties.maxAttempts();
        this.window = Duration.ofMinutes(properties.windowMinutes());
        this.maxEntries = properties.maxEntries();
    }

    /**
     * Anota un intento de inicio de sesión antes de comprobar las credenciales. Si el registro está
     * lleno de series bloqueadas, el intento no se anota y se deja pasar.
     *
     * @param email         email introducido; no distingue mayúsculas ni espacios en los extremos
     * @param clientAddress IP del cliente
     * @throws TooManyLoginAttemptsException si ya se han agotado los intentos de la ventana; en ese
     *     caso no se deben comprobar las credenciales
     */
    public void registerAttempt(String email, String clientAddress) {
        Instant now = clock.instant();
        String key = key(email, clientAddress);
        var newlyBlocked = new AtomicBoolean();
        Attempts current = attempts.computeIfPresent(key, (k, previous) -> next(previous, now, newlyBlocked));
        if (current == null) {
            current = registerNewSeries(key, now, newlyBlocked);
        }
        if (current == null) {
            return;
        }
        if (newlyBlocked.get()) {
            LOG.warn("Serie de intentos de inicio de sesión bloqueada por demasiados fallos");
        }
        if (current.count() > maxAttempts) {
            throw new TooManyLoginAttemptsException(secondsUntil(current.windowStart().plus(window), now));
        }
    }

    /**
     * Borra los intentos anotados tras un inicio de sesión correcto.
     *
     * @param email         email introducido
     * @param clientAddress IP del cliente
     */
    public void reset(String email, String clientAddress) {
        attempts.remove(key(email, clientAddress));
    }

    /**
     * Borra las series caducadas. Se ejecuta cada minuto.
     */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void purgeExpired() {
        Instant now = clock.instant();
        attempts.values().removeIf(entry -> isExpired(entry, now));
    }

    /**
     * Da de alta la serie (o la actualiza si otra petición la ha creado mientras tanto) bajo el
     * cerrojo de altas, haciendo sitio si el registro está lleno.
     *
     * @return la serie actualizada, o {@code null} si el registro está lleno de series bloqueadas y
     *     el intento no se anota
     */
    private Attempts registerNewSeries(String key, Instant now, AtomicBoolean newlyBlocked) {
        registrationLock.lock();
        try {
            if (!attempts.containsKey(key) && attempts.size() >= maxEntries && !evictOne(now)) {
                LOG.debug("Registro de intentos de inicio de sesión lleno de series bloqueadas: el intento no se anota");
                return null;
            }
            return attempts.compute(key, (k, previous) -> next(previous, now, newlyBlocked));
        } finally {
            registrationLock.unlock();
        }
    }

    /**
     * Expulsa una serie caducada o, si no hay, la no bloqueada más antigua. Se llama con el cerrojo
     * de altas tomado.
     *
     * @return {@code true} si se ha expulsado alguna serie
     */
    private boolean evictOne(Instant now) {
        Map.Entry<String, Attempts> oldestUnblocked = null;
        for (Map.Entry<String, Attempts> entry : attempts.entrySet()) {
            Attempts value = entry.getValue();
            if (isExpired(value, now)) {
                LOG.debug("Registro de intentos de inicio de sesión lleno: se expulsa una serie caducada");
                return attempts.remove(entry.getKey(), value) || evictOne(now);
            }
            if (!isBlocked(value) && (oldestUnblocked == null
                    || value.windowStart().isBefore(oldestUnblocked.getValue().windowStart()))) {
                oldestUnblocked = entry;
            }
        }
        if (oldestUnblocked == null) {
            return false;
        }
        LOG.debug("Registro de intentos de inicio de sesión lleno: se expulsa la serie no bloqueada más antigua");
        return attempts.remove(oldestUnblocked.getKey(), oldestUnblocked.getValue()) || evictOne(now);
    }

    /**
     * Calcula la serie tras un intento nuevo. El contador se detiene en {@code maxAttempts + 1},
     * que marca un intento rechazado; {@code newlyBlocked} indica el primer rechazo de la serie.
     */
    private Attempts next(Attempts previous, Instant now, AtomicBoolean newlyBlocked) {
        if (previous == null || isExpired(previous, now)) {
            return new Attempts(now, 1);
        }
        if (previous.count() == maxAttempts) {
            newlyBlocked.set(true);
        }
        return new Attempts(previous.windowStart(), Math.min(previous.count() + 1, maxAttempts + 1));
    }

    /**
     * Una serie no caducada está bloqueada cuando ya ha agotado sus intentos.
     */
    private boolean isBlocked(Attempts entry) {
        return entry.count() >= maxAttempts;
    }

    private boolean isExpired(Attempts entry, Instant now) {
        return !now.isBefore(entry.windowStart().plus(window));
    }

    /**
     * Segundos que faltan hasta {@code end}, redondeados hacia arriba y como mínimo 1.
     */
    private static long secondsUntil(Instant end, Instant now) {
        Duration remaining = Duration.between(now, end);
        long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }

    private static String key(String email, String clientAddress) {
        return User.normalizeEmail(email) + KEY_SEPARATOR + clientAddress;
    }

    /**
     * Serie de intentos de un email desde una IP.
     *
     * @param windowStart instante del primer intento de la serie
     * @param count       intentos anotados; {@code maxAttempts + 1} indica que se ha rechazado alguno
     */
    private record Attempts(Instant windowStart, int count) {
    }
}
