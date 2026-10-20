package com.expense.tracker.logger.task;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Lleva el contexto del hilo que encola la tarea al hilo que la ejecuta: el MDC completo
 * ({@code correlationId}, {@code userId} y las claves del consumidor, copiado a mano porque
 * {@link ContextPropagatingTaskDecorator} solo propaga los {@code ThreadLocalAccessor} registrados) y
 * el contexto de Observation (la traza sigue en el hilo {@code @Async}). Al terminar, el hilo vuelve a
 * su MDC previo, así un hilo de pool reusado no arrastra valores.
 */
public class MdcTaskDecorator implements TaskDecorator {

    private final ContextPropagatingTaskDecorator contextPropagation = new ContextPropagatingTaskDecorator();

    @Override
    public Runnable decorate(Runnable runnable) {
        var mdc = MDC.getCopyOfContextMap();
        var decorated = contextPropagation.decorate(runnable);
        return () -> {
            var previous = MDC.getCopyOfContextMap();
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                decorated.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
