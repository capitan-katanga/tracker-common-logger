package com.expense.tracker.logger.util;

/**
 * Nombres compartidos por el filtro, el interceptor y el pattern de logback: headers HTTP y claves MDC.
 */
public final class LogConstants {

    /** Header de correlación de negocio: se lee del request (o se genera) y se propaga a las llamadas salientes. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    /** Header de respuesta con el traceId de micrometer, para ubicar la traza en Tempo. */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_USER_ID = "userId";

    /** Clave MDC que publica micrometer-tracing (no la escribe la librería, solo la lee). */
    public static final String MDC_TRACE_ID = "traceId";

    public static final String MASK = "*****";

    private LogConstants() {
    }
}
