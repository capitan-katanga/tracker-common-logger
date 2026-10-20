package com.expense.tracker.logger.otel;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class OpenTelemetryAppenderInstallerTest {

    @Test
    @DisplayName("instala el appender con el SDK del contexto")
    void installsWithOpenTelemetry() {
        var beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("openTelemetry", OpenTelemetry.noop());
        var installer = new OpenTelemetryAppenderInstaller(beanFactory.getBeanProvider(OpenTelemetry.class));

        assertDoesNotThrow(installer::afterPropertiesSet);
    }

    @Test
    @DisplayName("sin SDK en el contexto no hace nada")
    void skipsWithoutOpenTelemetry() {
        var installer = new OpenTelemetryAppenderInstaller(
                new StaticListableBeanFactory().getBeanProvider(OpenTelemetry.class));

        assertDoesNotThrow(installer::afterPropertiesSet);
    }
}
