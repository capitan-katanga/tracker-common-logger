package com.expense.tracker.logger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Propiedades {@code tracker.logger.*} del logueo HTTP.
 *
 * @param http             toggles y exclusiones del logueo de entrada (server) y salida (client)
 * @param maskedHeaders    headers cuyo valor sale como {@code *****} (case-insensitive)
 * @param maskedBodyFields claves JSON cuyo valor sale como {@code *****} en los bodies
 * @param maxBodyLength    máximo de caracteres de body por línea de log
 */
@ConfigurationProperties("tracker.logger")
public record TrackerLoggerProperties(
        @DefaultValue Http http,
        @DefaultValue({"Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie", "X-Api-Key"})
        List<String> maskedHeaders,
        @DefaultValue({"password", "token", "accessToken", "refreshToken", "secret", "clientSecret", "apiKey"})
        List<String> maskedBodyFields,
        @DefaultValue("4096") int maxBodyLength) {

    /**
     * @param server logueo de los requests entrantes (filtro servlet)
     * @param client logueo de las llamadas salientes ({@code RestClient})
     */
    public record Http(@DefaultValue Server server, @DefaultValue Client client) {
    }

    /**
     * @param enabled       registra el filtro de logueo y correlación
     * @param excludedPaths patrones de path (sintaxis {@code PathPattern}) que no se loguean
     */
    public record Server(
            @DefaultValue("true") boolean enabled,
            @DefaultValue({"/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"})
            List<String> excludedPaths) {
    }

    /**
     * @param enabled engancha el interceptor de logueo a todo {@code RestClient.Builder} de Boot
     */
    public record Client(@DefaultValue("true") boolean enabled) {
    }
}
