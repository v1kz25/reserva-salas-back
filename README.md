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

## Configuración

Variables de entorno (hay un ejemplo en [`.env.example`](.env.example)):

| Variable | Por defecto | Descripción |
|---|---|---|
| `APP_TIMEZONE` | `Europe/Madrid` | Zona horaria (identificador IANA) con la que se calculan «hoy» y «mañana» para validar las fechas de las reservas. Si no es válida, la aplicación no arranca. |

## Compilar y probar

```bash
./mvnw verify   # compila y pasa los tests
./mvnw test     # solo los tests
```

## Auditoría de dependencias

- **Dependabot** (`.github/dependabot.yml`) revisa cada semana las dependencias de Maven y las GitHub Actions y abre PRs contra `develop`.
- **CI** (`.github/workflows/ci.yml`), siempre de forma informativa (no bloquea el build):
  - En las PRs, `dependency-review` avisa de las dependencias nuevas o actualizadas con vulnerabilidades conocidas (GitHub Advisory Database).
  - En cada build, `dependency-check` ejecuta [OWASP dependency-check](https://owasp.org/www-project-dependency-check/) sobre todas las dependencias. El resumen aparece en la página de la ejecución y el informe HTML/JSON se descarga como artifact `dependency-check-report`. La base de datos de NVD se guarda en caché entre ejecuciones.

Clave de la API de NVD (opcional): sin ella la primera descarga de la base de datos es muy lenta y puede fallar. Para usarla, [solicítala a NVD](https://nvd.nist.gov/developers/request-an-api-key) y créala como secreto `NVD_API_KEY` del repositorio (*Settings → Secrets and variables → Actions*). Si el secreto no existe, la auditoría se ejecuta igual sin clave.

En local, la auditoría va en un perfil para no ralentizar el build normal:

```bash
./mvnw -P dependency-check -DskipTests verify
# con clave: NVD_API_KEY=... ./mvnw -P dependency-check -DskipTests -DnvdApiKeyEnvironmentVariable=NVD_API_KEY verify
```

El informe queda en `target/dependency-check-report.html`.

## Flujo de trabajo

Git Flow: se trabaja en ramas `feature/<n>-<slug>` desde `develop` y se integran por PR. El CI tiene que pasar para poder fusionar. Consulta el [README del proyecto](https://github.com/v1kz25/reserva-salas) para más detalles.

## Licencia

[MIT](LICENSE)
