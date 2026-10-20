package com.expense.tracker.logger;

import com.expense.tracker.logger.config.TrackerLoggerProperties;

import java.util.List;

/**
 * Propiedades equivalentes a los defaults de {@code tracker.logger.*}, para tests sin contexto de Spring.
 */
public final class TestProperties {

    private TestProperties() {
    }

    public static TrackerLoggerProperties withMaxBodyLength(int maxBodyLength) {
        return new TrackerLoggerProperties(
                new TrackerLoggerProperties.Http(
                        new TrackerLoggerProperties.Server(true, List.of("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**")),
                        new TrackerLoggerProperties.Client(true)),
                List.of("Authorization", "Cookie"),
                List.of("password", "token"),
                maxBodyLength);
    }

    public static TrackerLoggerProperties defaults() {
        return withMaxBodyLength(4096);
    }
}
