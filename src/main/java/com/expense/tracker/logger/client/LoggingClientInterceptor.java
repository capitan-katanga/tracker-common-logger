package com.expense.tracker.logger.client;

import com.expense.tracker.logger.util.LogBodies;
import com.expense.tracker.logger.util.LogConstants;
import com.expense.tracker.logger.util.LogMasking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interceptor de salida para {@code RestClient}: propaga el {@code X-Correlation-Id} del MDC (si el request
 * no lo trae ya) y loguea {@code CLIENT REQUEST  --> ...} / {@code CLIENT RESPONSE <-- status (Nms) ...} con
 * headers y campos del body enmascarados.
 *
 * <p>El {@code traceparent} no lo agrega este interceptor: lo inyecta la instrumentación de Boot
 * ({@code ObservationRestClientCustomizer}) antes de que corran los interceptores, así que aparece en la
 * línea del request.</p>
 *
 * <p>La línea del request sale antes de ejecutar la llamada: si la conexión falla, queda registrado qué se
 * intentó mandar; un error de I/O sale como {@code CLIENT ERROR} (WARN) y se relanza. El body de la response
 * se bufferiza entero para loguearlo y re-servirlo.</p>
 */
public class LoggingClientInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingClientInterceptor.class);

    private final List<String> maskedHeaders;
    private final List<String> maskedBodyFields;
    private final int maxBodyLength;

    public LoggingClientInterceptor(List<String> maskedHeaders, List<String> maskedBodyFields, int maxBodyLength) {
        this.maskedHeaders = maskedHeaders;
        this.maskedBodyFields = maskedBodyFields;
        this.maxBodyLength = maxBodyLength;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        var correlationId = MDC.get(LogConstants.MDC_CORRELATION_ID);
        if (correlationId != null && !request.getHeaders().containsHeader(LogConstants.CORRELATION_ID_HEADER)) {
            request.getHeaders().set(LogConstants.CORRELATION_ID_HEADER, correlationId);
        }
        var method = request.getMethod().name();
        var uri = LogMasking.sanitize(request.getURI().toString());

        if (log.isInfoEnabled()) {
            log.atInfo()
                    .addKeyValue("http.method", method)
                    .addKeyValue("http.uri", uri)
                    .log("CLIENT REQUEST  --> {} {} | headers={} | body={}", method, uri,
                            LogMasking.formatHeaders(toMap(request.getHeaders()), maskedHeaders),
                            LogBodies.render(body, request.getHeaders().getContentType(), maxBodyLength, maskedBodyFields));
        }

        var start = System.nanoTime();
        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException e) {
            log.atWarn()
                    .addKeyValue("http.method", method)
                    .addKeyValue("http.uri", uri)
                    .addKeyValue("duration_ms", (System.nanoTime() - start) / 1_000_000)
                    .log("CLIENT ERROR    <-- {} {} | error={}", method, uri, LogMasking.sanitize(e.toString()));
            throw e;
        }
        var durationMs = (System.nanoTime() - start) / 1_000_000;
        var responseBody = response.getBody().readAllBytes();

        if (log.isInfoEnabled()) {
            var status = response.getStatusCode().value();
            log.atInfo()
                    .addKeyValue("http.method", method)
                    .addKeyValue("http.uri", uri)
                    .addKeyValue("http.status", status)
                    .addKeyValue("duration_ms", durationMs)
                    .log("CLIENT RESPONSE <-- {} ({}ms) {} {} | headers={} | body={}", status, durationMs, method, uri,
                            LogMasking.formatHeaders(toMap(response.getHeaders()), maskedHeaders),
                            LogBodies.render(responseBody, response.getHeaders().getContentType(), maxBodyLength,
                                    maskedBodyFields));
        }
        return new BufferedClientHttpResponse(response, responseBody);
    }

    private static Map<String, List<String>> toMap(HttpHeaders headers) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        headers.forEach(map::put);
        return map;
    }
}
