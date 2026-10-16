package com.reservasalas.repository;

import com.reservasalas.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de los ajustes de la aplicación, indexados por su clave.
 */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
