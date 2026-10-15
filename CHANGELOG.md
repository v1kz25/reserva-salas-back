# Changelog

Todos los cambios relevantes de este proyecto se documentan en este fichero.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/).

## [0.1.0] - 2026-10-15

### Añadido

- Listado de reservas con filtro por sala y por rango de fechas (`fechaDesde`, `fechaHasta`) (#1).
- Alta de reservas sin solapamientos en la misma sala, con `horaFin` posterior a `horaInicio` y solo a partir de mañana (#1).
- Zona horaria configurable con `APP_TIMEZONE` (por defecto `Europe/Madrid`) (#1).
- Consulta de una reserva (`GET /api/reservas/{id}`) y cancelación (`DELETE /api/reservas/{id}`), que libera la franja (#2).
- Respuestas de error `404`, `405` y `415` con `ProblemDetail` genérico, sin detalles internos (#11).

[0.1.0]: https://github.com/v1kz25/reserva-salas-back/releases/tag/v0.1.0
