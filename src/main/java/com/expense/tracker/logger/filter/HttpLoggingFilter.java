package com.expense.tracker.logger.filter;

import com.expense.tracker.logger.config.TrackerLoggerProperties;
import com.expense.tracker.logger.util.LogBodies;
import com.expense.tracker.logger.util.LogConstants;
import com.expense.tracker.logger.util.LogMasking;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Filtro de entrada: correlación y logueo del ciclo HTTP de cada request REST.
 *
 * <ul>
 *   <li>Toma el {@code X-Correlation-Id} del request (o genera un UUID), lo deja en el MDC como
 *       {@code correlationId} y lo devuelve en la response junto con el {@code X-Trace-Id} de micrometer.</li>
 *   <li>Loguea {@code REQUEST  --> METHOD uri | headers=... | body=...} antes de ejecutar la cadena y
 *       {@code RESPONSE <-- status (Nms) METHOD uri | headers=... | body=...} al terminar, con headers y
 *       campos del body enmascarados. Además agrega key-values ({@code http.method}, {@code http.status},
 *       {@code duration_ms}) que el appender OTLP manda a Loki como atributos.</li>
 *   <li>Solo loguea bodies textuales: un multipart o un binario se resume con tipo y tamaño.</li>
 * </ul>
 *
 * <p>Se registra con un {@code FilterRegistrationBean} justo después del {@code ServerHttpObservationFilter}
 * (así el {@code traceId} ya está en el MDC). No es {@code @Component} a propósito: Boot registraría el
 * bean una segunda vez.</p>
 *
 * <p>Al terminar limpia {@code correlationId} y {@code userId} del MDC ({@code userId} lo pone
 * {@link UserIdMdcFilter}, más adentro en la cadena, y tiene que llegar a la línea de RESPONSE).</p>
 */
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    private final List<PathPattern> excludedPaths;
    private final List<String> maskedHeaders;
    private final List<String> maskedBodyFields;
    private final int maxBodyLength;

    public HttpLoggingFilter(TrackerLoggerProperties properties) {
        this.excludedPaths = properties.http().server().excludedPaths().stream()
                .map(PathPatternParser.defaultInstance::parse)
                .toList();
        this.maskedHeaders = properties.maskedHeaders();
        this.maskedBodyFields = properties.maskedBodyFields();
        this.maxBodyLength = properties.maxBodyLength();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        return excludedPaths.stream().anyMatch(pattern -> pattern.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var correlationId = resolveCorrelationId(request);
        MDC.put(LogConstants.MDC_CORRELATION_ID, correlationId);
        response.setHeader(LogConstants.CORRELATION_ID_HEADER, correlationId);
        var traceId = MDC.get(LogConstants.MDC_TRACE_ID);
        if (traceId != null) {
            response.setHeader(LogConstants.TRACE_ID_HEADER, traceId);
        }

        var requestContentType = LogBodies.parseContentType(request.getContentType());
        var cachedRequest = shouldCacheBody(requestContentType) ? new CachedBodyRequestWrapper(request) : null;
        var requestToUse = cachedRequest != null ? cachedRequest : request;
        var wrappedResponse = new ContentCachingResponseWrapper(response);
        var method = request.getMethod();
        var uri = uriOf(request);

        logRequest(request, method, uri, requestContentType, cachedRequest);

        var start = System.nanoTime();
        Integer failedStatus = null;
        try {
            filterChain.doFilter(requestToUse, wrappedResponse);
        } catch (IOException | ServletException | RuntimeException e) {
            failedStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            throw e;
        } finally {
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            logResponse(wrappedResponse, method, uri, durationMs, failedStatus);
            wrappedResponse.copyBodyToResponse();
            MDC.remove(LogConstants.MDC_CORRELATION_ID);
            MDC.remove(LogConstants.MDC_USER_ID);
        }
    }

    private static String resolveCorrelationId(HttpServletRequest request) {
        var header = request.getHeader(LogConstants.CORRELATION_ID_HEADER);
        return header != null && !header.isBlank() ? LogMasking.sanitize(header.trim()) : UUID.randomUUID().toString();
    }

    /**
     * Cachea el body solo si es textual y no es un form: los forms y multiparts se leen por
     * {@code getParameter}/{@code getPart} y consumirles el stream acá los dejaría vacíos.
     */
    private static boolean shouldCacheBody(@Nullable MediaType contentType) {
        return contentType != null
                && LogBodies.isTextual(contentType)
                && !MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType);
    }

    private static String uriOf(HttpServletRequest request) {
        var query = request.getQueryString();
        return LogMasking.sanitize(query != null ? request.getRequestURI() + "?" + query : request.getRequestURI());
    }

    private void logRequest(HttpServletRequest request, String method, String uri, @Nullable MediaType contentType,
                            @Nullable CachedBodyRequestWrapper cachedRequest) {
        if (!log.isInfoEnabled()) {
            return;
        }
        String body;
        if (cachedRequest != null) {
            body = LogBodies.render(cachedRequest.getBody(), contentType, maxBodyLength, maskedBodyFields);
        } else if (contentType != null) {
            // Multipart, binario o form: no se lee el stream. Con chunked el largo no se conoce (-1).
            var length = request.getContentLengthLong();
            body = "<" + contentType + (length >= 0 ? ", " + length + " bytes" : "") + " omitido>";
        } else {
            body = "";
        }
        log.atInfo()
                .addKeyValue("http.method", method)
                .addKeyValue("http.uri", uri)
                .log("REQUEST  --> {} {} | headers={} | body={}",
                        method, uri, LogMasking.formatHeaders(requestHeaders(request), maskedHeaders), body);
    }

    private void logResponse(ContentCachingResponseWrapper response, String method, String uri, long durationMs,
                             @Nullable Integer failedStatus) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var status = failedStatus != null ? failedStatus : response.getStatus();
        var body = LogBodies.render(response.getContentAsByteArray(),
                LogBodies.parseContentType(response.getContentType()), maxBodyLength, maskedBodyFields);
        log.atInfo()
                .addKeyValue("http.method", method)
                .addKeyValue("http.uri", uri)
                .addKeyValue("http.status", status)
                .addKeyValue("duration_ms", durationMs)
                .log("RESPONSE <-- {} ({}ms) {} {} | headers={} | body={}",
                        status, durationMs, method, uri, LogMasking.formatHeaders(responseHeaders(response), maskedHeaders), body);
    }

    private static Map<String, List<String>> requestHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (var name : Collections.list(request.getHeaderNames())) {
            headers.put(name, Collections.list(request.getHeaders(name)).stream().map(LogMasking::sanitize).toList());
        }
        return headers;
    }

    private static Map<String, List<String>> responseHeaders(HttpServletResponse response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (var name : response.getHeaderNames()) {
            headers.putIfAbsent(name, List.copyOf(response.getHeaders(name)));
        }
        return headers;
    }
}
