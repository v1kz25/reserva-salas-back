package com.reservasalas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas, como la limpieza periódica de los intentos de inicio de sesión
 * caducados.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
