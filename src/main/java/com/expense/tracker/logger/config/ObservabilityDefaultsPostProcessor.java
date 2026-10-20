package com.expense.tracker.logger.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aporta los defaults de observabilidad como property source de mínima precedencia: cualquier
 * {@code application.yaml}, variable de entorno o argumento del consumidor los pisa.
 *
 * <p>Es un {@code EnvironmentPostProcessor} y no un {@code application.yaml} dentro del JAR porque el
 * classpath carga un solo {@code application.yaml}: el del consumidor taparía al de la librería.</p>
 *
 * <p>Defaults: exportación OTLP/HTTP de métricas, trazas y logs al OTel Collector local (puerto 4318),
 * muestreo del 100% de las trazas y el pattern de correlación de la consola con {@code traceId},
 * {@code spanId}, {@code correlationId} y {@code userId}.</p>
 */
public class ObservabilityDefaultsPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "trackerLoggerObservabilityDefaults";

    static final String OTLP_BASE_URL = "http://localhost:4318";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("management.otlp.metrics.export.url", OTLP_BASE_URL + "/v1/metrics");
        defaults.put("management.opentelemetry.tracing.export.otlp.endpoint", OTLP_BASE_URL + "/v1/traces");
        defaults.put("management.opentelemetry.logging.export.otlp.endpoint", OTLP_BASE_URL + "/v1/logs");
        defaults.put("management.tracing.sampling.probability", "1.0");
        // El espacio final separa el bloque de correlación del nombre del logger en el pattern de Boot.
        defaults.put("logging.pattern.correlation",
                "[%X{traceId:-},%X{spanId:-}] [%X{correlationId:-}] [%X{userId:-}] ");
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }
}
