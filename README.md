# reserva-salas-back

API REST de la aplicación de reserva de salas. El contrato de la API está en [`reserva-salas`](https://github.com/v1kz25/reserva-salas/blob/develop/contrato/openapi.yaml).

## Stack

- Java 21
- Spring Boot 4 (Web MVC, Data JPA, Validation, Security)
- H2 en memoria
- Maven (wrapper incluido)

## Arranque

Sin un administrador no se puede iniciar sesión, así que define al menos `ADMIN_EMAIL` y `ADMIN_PASSWORD` (en la shell o copiando `.env.example` a `.env`, que no se sube al repositorio):

```bash
cp .env.example .env       # y rellena ADMIN_PASSWORD (y, si quieres, DEMO_EMAIL y DEMO_PASSWORD)
set -a; source .env; set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La API queda en `http://localhost:8080/api`. Con el perfil `dev`:

- Se permite CORS con credenciales desde `http://localhost:4200` (el frontend).
- Si están definidas `DEMO_EMAIL` y `DEMO_PASSWORD`, se crea además un usuario de prueba con rol `USER`.
- La consola de H2 está en `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:reservasalas`, usuario `sa`, sin contraseña).

## Configuración

Variables de entorno (hay un ejemplo en [`.env.example`](.env.example)):

| Variable | Por defecto | Descripción |
|---|---|---|
| `APP_TIMEZONE` | `Europe/Madrid` | Zona horaria inicial con la que se calculan «hoy» y «mañana». Solo se usa si no hay ninguna guardada (ver [Zona horaria](#zona-horaria)). Si no es válida, la aplicación no arranca. |
| `APP_COOKIE_SECURE` | `false` | Marca las cookies `JSESSIONID` y `XSRF-TOKEN` como `Secure` (solo HTTPS). Ponlo a `true` en producción. |
| `LOGIN_MAX_INTENTOS` | `5` | Intentos fallidos de inicio de sesión permitidos por email e IP dentro de la ventana (ver [Límite de intentos de inicio de sesión](#límite-de-intentos-de-inicio-de-sesión)). |
| `LOGIN_VENTANA_MINUTOS` | `15` | Duración de la ventana en minutos, desde el primer intento fallido. |
| `LOGIN_MAX_ENTRADAS` | `10000` | Combinaciones email e IP que se guardan como máximo en memoria. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `none` | Ponlo a `native` solo detrás de un proxy inverso de confianza, para tomar la IP del cliente de `X-Forwarded-For`. |
| `ADMIN_EMAIL` | — | Email del administrador inicial. |
| `ADMIN_PASSWORD` | — | Contraseña del administrador inicial (máximo 72 bytes). Si falta, no se crea y se avisa en el log. |
| `ADMIN_NOMBRE` | `Administrador` | Nombre del administrador inicial. |
| `DEMO_EMAIL`, `DEMO_PASSWORD` | — | Usuario de prueba con rol `USER`; solo con el perfil `dev`. |
| `DEMO_NOMBRE` | `Usuario de prueba` | Nombre del usuario de prueba. |

Las cuentas iniciales solo se crean si no existe ya un usuario con ese email; si existe, no se modifica. La base de datos es en memoria, así que se crean en cada arranque.

## Zona horaria

«Hoy» y «mañana» (reservas solo a partir de mañana, salas con reservas futuras) se calculan con la zona horaria vigente:

- `GET /api/config/zona-horaria`: devuelve la zona vigente, p. ej. `{"zonaHoraria":"Europe/Madrid"}`. Para cualquier usuario autenticado.
- `PUT /api/config/zona-horaria` con el mismo cuerpo: la cambia. Solo `ADMIN` (`403` si no). Se aplica en caliente y se guarda en la tabla `APP_SETTINGS`. Responde `400` si el valor falta, está vacío o no es válido.

`APP_TIMEZONE` es solo la zona inicial: si hay una zona guardada en `APP_SETTINGS`, manda la guardada.

Solo se admiten identificadores IANA de la forma Región/Ciudad (`Europe/Madrid`, `America/New_York`...) y `UTC`, distinguiendo mayúsculas. No se admiten desfases fijos (`+02:00`, `UTC+2`, `Etc/GMT+1`...) porque no siguen el horario de verano: en el `PUT` dan `400` y en `APP_TIMEZONE` hacen fallar el arranque.

Con varias instancias, cada una mantiene la zona en memoria: un cambio solo se aplica en la instancia que lo recibe, y las demás lo toman al reiniciarse.

## Autenticación

La API usa sesión HTTP en cookie (`JSESSIONID`, `HttpOnly`, `SameSite=Strict`) y protección CSRF con la cookie `XSRF-TOKEN`, que el cliente repite en la cabecera `X-XSRF-TOKEN` en cada `POST`, `PUT` y `DELETE` (incluido el login). Todo `/api/**` exige sesión salvo `GET /api/auth/csrf` y `POST /api/auth/login`.

Ejemplo con `curl` y un fichero de cookies:

```bash
# 1. Obtener la cookie XSRF-TOKEN (no crea sesión)
curl -s -c cookies.txt http://localhost:8080/api/auth/csrf
XSRF=$(awk '$6 == "XSRF-TOKEN" {print $7}' cookies.txt)

# 2. Iniciar sesión (cambia el token CSRF: hay que volver a leerlo)
curl -s -b cookies.txt -c cookies.txt -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
     -d '{"email":"admin@example.com","password":"<contraseña>"}' http://localhost:8080/api/auth/login
XSRF=$(awk '$6 == "XSRF-TOKEN" {print $7}' cookies.txt)

# 3. Peticiones con la sesión
curl -s -b cookies.txt http://localhost:8080/api/auth/me
curl -s -b cookies.txt -c cookies.txt -H "X-XSRF-TOKEN: $XSRF" -X DELETE http://localhost:8080/api/reservas/1

# 4. Cerrar sesión
curl -s -b cookies.txt -c cookies.txt -H "X-XSRF-TOKEN: $XSRF" -X POST http://localhost:8080/api/auth/logout
```

### Límite de intentos de inicio de sesión

`POST /api/auth/login` cuenta los intentos por email (sin distinguir mayúsculas ni espacios en los extremos) e IP del cliente:

- Tras `LOGIN_MAX_INTENTOS` intentos fallidos dentro de una ventana de `LOGIN_VENTANA_MINUTOS`, el login responde `429` con un `ProblemDetail` y la cabecera `Retry-After` (segundos que faltan, redondeados hacia arriba). El mensaje es el mismo exista o no el email.
- La ventana es fija y empieza con el primer intento fallido de la serie: el bloqueo dura hasta que termina, y los intentos rechazados no lo alargan. Así nunca se comprueban más de `LOGIN_MAX_INTENTOS` contraseñas por ventana.
- Mientras dura el bloqueo no se comprueban las credenciales, ni siquiera si son correctas.
- Un inicio de sesión correcto reinicia el contador. Los `400` por cuerpo no válido no cuentan.
- El contador está en memoria de cada instancia: se pierde al reiniciar y, con varias instancias, cada una cuenta por separado.
- Guarda como máximo `LOGIN_MAX_ENTRADAS` combinaciones. Las caducadas se borran cada minuto. Si al llegar una combinación nueva el registro está lleno, se expulsa una caducada; si no hay, la no bloqueada más antigua; y si todas están bloqueadas, la nueva no se registra y ese intento se procesa sin contar. Así, llenar el registro con emails inventados no desbloquea a nadie; a cambio, mientras esté lleno solo de combinaciones bloqueadas, las combinaciones nuevas no tienen límite (hacen falta decenas de miles de peticiones por ventana para llegar ahí). Un límite global por IP como respaldo está pendiente en la issue #31.
- Al bloquearse una serie se escribe un único aviso (`WARN`) en el log; cada petición rechazada después solo se registra en `DEBUG`. Ninguno incluye el email ni la IP.
- Por diseño, la clave es la pareja email e IP: frena la fuerza bruta contra una cuenta desde un mismo origen, pero no el *password spraying* (muchas IPs contra un mismo email, o una IP probando pocas contraseñas contra muchos emails). Contra eso hacen falta otras medidas (límite por IP o por cuenta, CAPTCHA, un WAF en el proxy).

La IP del cliente es la de la conexión (`getRemoteAddr()`); la cabecera `X-Forwarded-For` se ignora, porque cualquiera puede falsearla. `SERVER_FORWARD_HEADERS_STRATEGY` vale `none` por defecto, también en plataformas cloud donde Spring Boot lo activaría solo. Si la API está detrás de un proxy inverso de confianza, define `SERVER_FORWARD_HEADERS_STRATEGY=native`: Tomcat tomará la IP de `X-Forwarded-For` solo cuando la conexión venga de una IP de proxy interna (redes privadas y `localhost`; se puede ajustar con `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`). Sin esa configuración, todos los clientes que lleguen por el proxy comparten su IP.

## Compilar y probar

```bash
./mvnw verify   # compila y pasa los tests
./mvnw test     # solo los tests
```

## Auditoría de dependencias

- **Dependabot** (`.github/dependabot.yml`) revisa cada semana las dependencias de Maven y las GitHub Actions y abre PRs contra `develop`.
- **CI** (`.github/workflows/ci.yml`), siempre de forma informativa (no bloquea el build):
  - En las PRs, `dependency-review` avisa de las dependencias nuevas o actualizadas con vulnerabilidades conocidas (GitHub Advisory Database).
  - En cada build, `dependency-check` ejecuta [OWASP dependency-check](https://owasp.org/www-project-dependency-check/) sobre todas las dependencias. El resumen aparece en la página de la ejecución y el informe HTML/JSON se descarga como artifact `dependency-check-report`. La base de datos de NVD se guarda en caché entre ejecuciones. Necesita una clave de la API de NVD (ver abajo).

Clave de la API de NVD: sin ella, la API de NVD rechaza las peticiones y dependency-check no puede ejecutarse. **Si el secreto no existe, el job `dependency-check` no audita nada**: solo deja un aviso en el resumen de la ejecución, y la única auditoría activa es `dependency-review` en las PRs. Para activarla, [solicita una clave a NVD](https://nvd.nist.gov/developers/request-an-api-key) y créala como secreto `NVD_API_KEY` del repositorio (*Settings → Secrets and variables → Actions*). Las PRs de Dependabot no ven esos secretos: para auditarlas también, crea el mismo secreto en *Settings → Secrets and variables → Dependabot*.

En local, la auditoría va en un perfil para no ralentizar el build normal (también necesita la clave):

```bash
NVD_API_KEY=... ./mvnw -P dependency-check -DskipTests -DnvdApiKeyEnvironmentVariable=NVD_API_KEY verify
```

El informe queda en `target/dependency-check-report.html`.

## Flujo de trabajo

Git Flow: se trabaja en ramas `feature/<n>-<slug>` desde `develop` y se integran por PR. El CI tiene que pasar para poder fusionar. Consulta el [README del proyecto](https://github.com/v1kz25/reserva-salas) para más detalles.

## Licencia

[MIT](LICENSE)
