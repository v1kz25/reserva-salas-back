package com.reservasalas.controller;

import com.reservasalas.dto.TimeZoneDto;
import com.reservasalas.service.TimeZoneService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de configuración de la aplicación. Consultar la zona horaria es para cualquier usuario
 * autenticado; cambiarla es solo para administradores, restricción que se aplica en
 * {@link com.reservasalas.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final TimeZoneService timeZoneService;

    /**
     * Crea el controlador.
     *
     * @param timeZoneService servicio de la zona horaria
     */
    public ConfigController(TimeZoneService timeZoneService) {
        this.timeZoneService = timeZoneService;
    }

    /**
     * Obtiene la zona horaria de la aplicación.
     *
     * @return la zona horaria vigente
     */
    @GetMapping("/zona-horaria")
    public ResponseEntity<TimeZoneDto> getTimeZone() {
        return ResponseEntity.ok(timeZoneService.get());
    }

    /**
     * Cambia la zona horaria de la aplicación.
     *
     * @param request zona horaria nueva
     * @return la zona horaria aplicada
     */
    @PutMapping("/zona-horaria")
    public ResponseEntity<TimeZoneDto> changeTimeZone(@Valid @RequestBody TimeZoneDto request) {
        return ResponseEntity.ok(timeZoneService.change(request));
    }
}
