package com.reservasalas.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Zona horaria vigente de la aplicación, con la que se calcula qué día es hoy. Arranca con la zona
 * del {@link Clock} (la de {@code APP_TIMEZONE}) y el administrador puede cambiarla en caliente.
 *
 * <p>Solo se admiten identificadores IANA de la forma Región/Ciudad ({@code Europe/Madrid},
 * {@code America/Argentina/Buenos_Aires}...) y {@code UTC}. Se rechazan los desfases fijos como
 * {@code +02:00} o {@code UTC+2}, las zonas {@code Etc/} (además, {@code Etc/GMT+1} es UTC-1, con el
 * signo invertido), las {@code SystemV/} y los alias sin región ({@code GMT0}, {@code EST5EDT}...):
 * no siguen los cambios de horario de verano de un lugar concreto o se interpretan mal con
 * facilidad, y darían un «hoy» incorrecto durante parte del año.
 */
@Component
public class CurrentTimeZone {

    private static final String UTC_ID = "UTC";
    private static final List<String> EXCLUDED_PREFIXES = List.of("Etc/", "SystemV/");
    private static final Set<String> REGION_IDS = ZoneId.getAvailableZoneIds().stream()
            .filter(CurrentTimeZone::isRegionCityId)
            .collect(Collectors.toUnmodifiableSet());

    private final Clock clock;
    private volatile ZoneId zone;

    /**
     * Crea la zona vigente a partir del reloj de la aplicación.
     *
     * @param clock reloj de la aplicación; su zona es la zona inicial
     */
    public CurrentTimeZone(Clock clock) {
        this.clock = clock;
        this.zone = clock.getZone();
    }

    /**
     * Convierte un identificador en zona horaria si es un identificador IANA de la forma
     * Región/Ciudad o {@code UTC}. Se ignoran los espacios de los extremos; se distinguen mayúsculas
     * y minúsculas.
     *
     * @param id identificador recibido; puede ser nulo
     * @return la zona, o vacío si el identificador es nulo, está en blanco o no es Región/Ciudad ni
     *     {@code UTC}
     */
    public static Optional<ZoneId> parseRegionId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String stripped = id.strip();
        if (!REGION_IDS.contains(stripped)) {
            return Optional.empty();
        }
        return Optional.of(ZoneId.of(stripped));
    }

    /**
     * Zona horaria vigente.
     *
     * @return la zona
     */
    public ZoneId zone() {
        return zone;
    }

    /**
     * Día de hoy en la zona horaria vigente, según el reloj de la aplicación.
     *
     * @return la fecha de hoy
     */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    /**
     * Cambia la zona horaria vigente; se aplica desde la siguiente consulta.
     *
     * @param newZone nueva zona horaria
     * @throws NullPointerException si {@code newZone} es nulo
     */
    public void change(ZoneId newZone) {
        this.zone = Objects.requireNonNull(newZone, "newZone");
    }

    /**
     * Indica si un identificador de la base de zonas es {@code UTC} o tiene la forma Región/Ciudad,
     * excluyendo los prefijos de desfases fijos ({@code Etc/}, {@code SystemV/}).
     */
    private static boolean isRegionCityId(String id) {
        if (UTC_ID.equals(id)) {
            return true;
        }
        return id.indexOf('/') > 0 && EXCLUDED_PREFIXES.stream().noneMatch(id::startsWith);
    }
}
