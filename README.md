# reserva-salas-back

API REST de la aplicación de reserva de salas. El contrato de la API está en [`reserva-salas`](https://github.com/v1kz25/reserva-salas/blob/develop/contrato/openapi.yaml).

## Stack

- Java 21
- Spring Boot 4 (Web MVC, Data JPA, Validation)
- H2 en memoria
- Maven (wrapper incluido)

## Arranque

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La API queda en `http://localhost:8080/api`. Con el perfil `dev`:

- Se permite CORS desde `http://localhost:4200` (el frontend).
- La consola de H2 está en `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:reservasalas`, usuario `sa`, sin contraseña).

## Compilar y probar

```bash
./mvnw verify   # compila y pasa los tests
./mvnw test     # solo los tests
```

## Flujo de trabajo

Git Flow: se trabaja en ramas `feature/<n>-<slug>` desde `develop` y se integran por PR. El CI tiene que pasar para poder fusionar. Consulta el [README del proyecto](https://github.com/v1kz25/reserva-salas) para más detalles.

## Licencia

[MIT](LICENSE)
