package com.reservasalas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ajuste de la aplicación que el administrador puede cambiar en caliente, guardado como par
 * clave-valor (p. ej. la zona horaria).
 */
@Entity
@Table(name = "APP_SETTINGS")
public class AppSetting {

    /** Clave del ajuste con la zona horaria de la aplicación. */
    public static final String TIME_ZONE_KEY = "zona-horaria";

    /** Longitud máxima del valor de un ajuste. */
    public static final int MAX_VALUE_LENGTH = 100;

    @Id
    @Column(name = "SETTING_KEY", length = 50)
    private String key;

    @Column(name = "SETTING_VALUE", nullable = false, length = MAX_VALUE_LENGTH)
    private String value;

    protected AppSetting() {
        // Requerido por JPA
    }

    /**
     * Crea un ajuste, aún sin persistir.
     *
     * @param key   clave del ajuste
     * @param value valor del ajuste
     */
    public AppSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Clave del ajuste.
     *
     * @return la clave
     */
    public String getKey() {
        return key;
    }

    /**
     * Valor del ajuste.
     *
     * @return el valor
     */
    public String getValue() {
        return value;
    }
}
