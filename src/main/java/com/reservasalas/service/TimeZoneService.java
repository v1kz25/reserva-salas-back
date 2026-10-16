package com.reservasalas.service;

import com.reservasalas.config.CurrentTimeZone;
import com.reservasalas.dto.TimeZoneDto;
import com.reservasalas.entity.AppSetting;
import com.reservasalas.exception.InvalidTimeZoneException;
import com.reservasalas.repository.AppSettingRepository;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;

/**
 * Consulta y cambio de la zona horaria de la aplicación. El cambio se guarda en la base de datos y
 * se aplica en caliente; al arrancar se recupera la zona guardada, si la hay.
 */
@Service
public class TimeZoneService implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(TimeZoneService.class);

    private final AppSettingRepository appSettingRepository;
    private final CurrentTimeZone currentTimeZone;

    /**
     * Crea el servicio.
     *
     * @param appSettingRepository repositorio de ajustes de la aplicación
     * @param currentTimeZone      zona horaria vigente
     */
    public TimeZoneService(AppSettingRepository appSettingRepository, CurrentTimeZone currentTimeZone) {
        this.appSettingRepository = appSettingRepository;
        this.currentTimeZone = currentTimeZone;
    }

    /**
     * Obtiene la zona horaria vigente.
     *
     * @return la zona horaria
     */
    public TimeZoneDto get() {
        return new TimeZoneDto(currentTimeZone.zone().getId());
    }

    /**
     * Cambia la zona horaria: la guarda y después la aplica. No es {@code @Transactional} a
     * propósito: el guardado es una sola operación con su propia transacción, de modo que la zona
     * solo se aplica si se ha guardado, y el {@code synchronized} cubre también el commit para que
     * dos cambios simultáneos no dejen en memoria una zona distinta de la guardada.
     *
     * @param request zona horaria pedida
     * @return la zona horaria aplicada, con el identificador normalizado
     * @throws InvalidTimeZoneException si no es un identificador IANA de región válido
     */
    public synchronized TimeZoneDto change(TimeZoneDto request) {
        ZoneId zone = CurrentTimeZone.parseRegionId(request.timeZone())
                .orElseThrow(InvalidTimeZoneException::new);
        appSettingRepository.save(new AppSetting(AppSetting.TIME_ZONE_KEY, zone.getId()));
        ZoneId previous = currentTimeZone.zone();
        currentTimeZone.change(zone);
        LOG.info("Zona horaria cambiada de {} a {}", previous.getId(), zone.getId());
        return new TimeZoneDto(zone.getId());
    }

    /**
     * Al arrancar, en cuanto están creados todos los beans y antes de que el servidor web acepte
     * peticiones, aplica la zona guardada en la base de datos. Si no hay ninguna, se mantiene la de
     * {@code APP_TIMEZONE}; si la guardada no es válida, se ignora con un aviso.
     *
     * @throws org.springframework.dao.DataAccessException si no se puede leer la base de datos; el
     *     arranque falla para no atender peticiones con una zona distinta de la guardada
     */
    @Override
    public synchronized void afterSingletonsInstantiated() {
        Optional<String> stored = appSettingRepository.findById(AppSetting.TIME_ZONE_KEY).map(AppSetting::getValue);
        if (stored.isEmpty()) {
            LOG.info("Sin zona horaria guardada; se usa {}", currentTimeZone.zone().getId());
            return;
        }
        CurrentTimeZone.parseRegionId(stored.get()).ifPresentOrElse(
                zone -> {
                    currentTimeZone.change(zone);
                    LOG.info("Zona horaria guardada aplicada: {}", zone.getId());
                },
                () -> LOG.warn("La zona horaria guardada no es válida; se usa {}", currentTimeZone.zone().getId()));
    }
}
