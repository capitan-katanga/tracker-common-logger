package com.expense.tracker.logger.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Conecta el appender {@code OTEL} del {@code logback-spring.xml} con el SDK de OpenTelemetry que
 * autoconfigura Boot, así los logs salen por OTLP hacia Loki. Hasta que se instala, el appender
 * bufferea los primeros eventos del arranque. Sin SDK en el contexto no hace nada.
 */
public class OpenTelemetryAppenderInstaller implements InitializingBean {

    private final ObjectProvider<OpenTelemetry> openTelemetry;

    public OpenTelemetryAppenderInstaller(ObjectProvider<OpenTelemetry> openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void afterPropertiesSet() {
        openTelemetry.ifAvailable(OpenTelemetryAppender::install);
    }
}
