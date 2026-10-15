package com.reservasalas.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente HTTP de prueba contra un servidor real, con su propio almacén de cookies y sin
 * comportamientos automáticos (redirecciones, reintentos), para comprobar cabeceras y cookies
 * tal y como las ve un navegador.
 */
public final class TestHttpClient {

    /** Respuesta con las cabeceras {@code Set-Cookie} sin interpretar. */
    public record Reply(int status, String body, HttpResponse<String> raw, List<String> setCookies) {

        /**
         * Cabecera {@code Set-Cookie} completa de la cookie indicada.
         *
         * @param name nombre de la cookie
         * @return la cabecera, si la respuesta la emite
         */
        public Optional<String> setCookie(String name) {
            return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst();
        }

        /**
         * Valor de una cabecera de la respuesta.
         *
         * @param name nombre de la cabecera
         * @return su primer valor, si existe
         */
        public Optional<String> header(String name) {
            return raw.headers().firstValue(name);
        }
    }

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final String baseUrl;

    /**
     * Crea el cliente.
     *
     * @param port puerto del servidor local
     */
    public TestHttpClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    /**
     * Valor actual de una cookie.
     *
     * @param name nombre de la cookie
     * @return su valor, o {@code null} si no la tiene
     */
    public String cookie(String name) {
        return cookies.get(name);
    }

    /**
     * Fija una cookie a mano (p. ej. para repetir un identificador de sesión antiguo).
     *
     * @param name  nombre
     * @param value valor
     */
    public void setCookie(String name, String value) {
        cookies.put(name, value);
    }

    /**
     * GET sin cuerpo.
     *
     * @param path ruta
     * @return la respuesta
     */
    public Reply get(String path) {
        return send("GET", path, null, true);
    }

    /**
     * Petición con el token CSRF actual (cookie {@code XSRF-TOKEN}) en {@code X-XSRF-TOKEN}.
     *
     * @param method método HTTP
     * @param path   ruta
     * @param json   cuerpo JSON, o {@code null}
     * @return la respuesta
     */
    public Reply send(String method, String path, String json) {
        return send(method, path, json, true);
    }

    /**
     * Petición con control sobre la cabecera CSRF.
     *
     * @param method   método HTTP
     * @param path     ruta
     * @param json     cuerpo JSON, o {@code null}
     * @param withCsrf si se envía {@code X-XSRF-TOKEN} con el valor de la cookie
     * @return la respuesta
     */
    public Reply send(String method, String path, String json, boolean withCsrf) {
        return send(method, path, json, withCsrf ? cookies.get("XSRF-TOKEN") : null);
    }

    /**
     * Petición con un token CSRF explícito.
     *
     * @param method    método HTTP
     * @param path      ruta
     * @param json      cuerpo JSON, o {@code null}
     * @param csrfToken valor de {@code X-XSRF-TOKEN}, o {@code null} para no enviarla
     * @return la respuesta
     */
    public Reply send(String method, String path, String json, String csrfToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .method(method, json == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        if (!cookies.isEmpty()) {
            List<String> pairs = new ArrayList<>();
            cookies.forEach((k, v) -> pairs.add(k + "=" + v));
            builder.header("Cookie", String.join("; ", pairs));
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            List<String> setCookies = response.headers().allValues("Set-Cookie");
            for (String header : setCookies) {
                String pair = header.split(";", 2)[0];
                int eq = pair.indexOf('=');
                String name = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                if (value.isEmpty() || header.toLowerCase().contains("max-age=0")) {
                    cookies.remove(name);
                } else {
                    cookies.put(name, value);
                }
            }
            return new Reply(response.statusCode(), response.body(), response, setCookies);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
