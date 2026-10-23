package com.expense.tracker.logger;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;

class LogbackBaseConfigTest {

    private static final String CONSOLE_LOG_PATTERN = "CONSOLE_LOG_PATTERN";

    @Test
    @DisplayName("el pattern de consola no muestra PID, app name ni hilo y usa el nombre simple del logger")
    void consolePatternIsShort() throws JoranException {
        var pattern = consolePattern();

        // ${PID} ya llega resuelto al encoder: lo que delata al PID es su color.
        assertAll(
                () -> assertThat(pattern, not(containsString("magenta"))),
                () -> assertThat(pattern, not(containsString("APPLICATION_NAME"))),
                () -> assertThat(pattern, not(containsString("%15.15t"))),
                () -> assertThat(pattern, containsString("%clr(%logger{0}){cyan}")));
    }

    @Test
    @DisplayName("logging.pattern.console del consumidor pisa el pattern de la librería")
    void consumerPatternWins() throws JoranException {
        System.setProperty(CONSOLE_LOG_PATTERN, "%m%n");
        try {
            assertThat(consolePattern(), equalTo("%m%n"));
        } finally {
            System.clearProperty(CONSOLE_LOG_PATTERN);
        }
    }

    /** Configura un contexto propio (no el global, que usa {@link LogCapture}) con la config de la librería. */
    private static String consolePattern() throws JoranException {
        var context = new LoggerContext();
        try {
            var configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(LogbackBaseConfigTest.class.getResource("/logback-spring.xml"));
            var console = (ConsoleAppender<?>) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
            return ((PatternLayoutEncoder) console.getEncoder()).getPattern();
        } finally {
            context.stop();
        }
    }
}
