package com.expense.tracker.logger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

class ObservabilityDefaultsPostProcessorTest {

    private final ObservabilityDefaultsPostProcessor postProcessor = new ObservabilityDefaultsPostProcessor();

    @Test
    @DisplayName("aporta los endpoints OTLP, el muestreo y el pattern de correlación")
    void contributesDefaults() {
        var environment = new StandardEnvironment();

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.otlp.metrics.export.url"), equalTo("http://localhost:4318/v1/metrics"));
        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"),
                equalTo("http://localhost:4318/v1/traces"));
        assertThat(environment.getProperty("management.opentelemetry.logging.export.otlp.endpoint"),
                equalTo("http://localhost:4318/v1/logs"));
        assertThat(environment.getProperty("management.tracing.sampling.probability"), equalTo("1.0"));
        assertThat(environment.getProperty("logging.pattern.correlation"), startsWith("[%X{traceId:-},%X{spanId:-}]"));
    }

    @Test
    @DisplayName("la config del consumidor pisa los defaults (precedencia mínima)")
    void consumerOverridesDefaults() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("app",
                Map.of("management.tracing.sampling.probability", "0.1")));

        postProcessor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.tracing.sampling.probability"), equalTo("0.1"));
        assertThat(environment.getPropertySources().stream().toList().getLast().getName(),
                equalTo(ObservabilityDefaultsPostProcessor.PROPERTY_SOURCE_NAME));
    }
}
