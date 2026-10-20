package com.expense.tracker.logger.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("la tarea ve el MDC del hilo que la encoló y el hilo recupera su MDC previo")
    void propagatesAndRestoresMdc() {
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();
        MDC.put("correlationId", "abc");
        var task = decorator.decorate(() -> seen.set(MDC.getCopyOfContextMap()));
        MDC.clear();
        MDC.put("other", "1");

        task.run();

        assertThat(seen.get(), equalTo(Map.of("correlationId", "abc")));
        assertThat(MDC.getCopyOfContextMap(), equalTo(Map.of("other", "1")));
    }

    @Test
    @DisplayName("en otro hilo sin MDC previo, el MDC queda limpio al terminar")
    void clearsMdcWhenThereWasNone() throws InterruptedException {
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicReference<Map<String, String>> after = new AtomicReference<>();
        MDC.put("correlationId", "abc");
        var task = decorator.decorate(() -> seen.set(MDC.get("correlationId")));

        var thread = Thread.ofVirtual().start(() -> {
            task.run();
            after.set(MDC.getCopyOfContextMap());
        });
        thread.join();

        assertThat(seen.get(), equalTo("abc"));
        assertThat(after.get(), anyOf(nullValue(), anEmptyMap()));
    }

    @Test
    @DisplayName("sin MDC al encolar, la tarea corre igual")
    void runsWithoutCapturedMdc() throws InterruptedException {
        AtomicReference<String> seen = new AtomicReference<>("sin correr");
        var thread = Thread.ofVirtual().start(() -> decorator.decorate(() -> seen.set(MDC.get("correlationId"))).run());
        thread.join();

        assertThat(seen.get(), nullValue());
    }
}
