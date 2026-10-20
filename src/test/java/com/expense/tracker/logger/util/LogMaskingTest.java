package com.expense.tracker.logger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class LogMaskingTest {

    @Test
    @DisplayName("formatHeaders enmascara sin distinguir mayúsculas y separa los multi-valor")
    void formatsAndMasksHeaders() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("authorization", List.of("Bearer abc"));
        headers.put("Accept", List.of("a", "b"));
        headers.put("X-Trace", List.of("t,1"));

        var formatted = LogMasking.formatHeaders(headers, List.of(" Authorization "));

        assertThat(formatted, equalTo("{authorization=\"*****\", Accept=[\"a\", \"b\"], X-Trace=\"t,1\"}"));
    }

    @Test
    @DisplayName("formatHeaders de un mapa vacío devuelve {}")
    void formatsEmptyHeaders() {
        assertThat(LogMasking.formatHeaders(Map.of(), List.of("Authorization")), equalTo("{}"));
    }

    @Test
    @DisplayName("maskBody enmascara strings y escalares conservando el espaciado")
    void masksBodyFields() {
        var body = "{\"user\":\"jdoe\", \"password\" : \"s3\\\"cr\", \"pin\": 1234, \"nested\":{\"token\":\"x\"}}";

        var masked = LogMasking.maskBody(body, List.of("password", "pin", "token", " ", "missing"));

        assertThat(masked, equalTo(
                "{\"user\":\"jdoe\", \"password\" : \"*****\", \"pin\": \"*****\", \"nested\":{\"token\":\"*****\"}}"));
    }

    @Test
    @DisplayName("maskBody devuelve el body tal cual si no hay nada que enmascarar")
    void maskBodyPassthrough() {
        assertThat(LogMasking.maskBody(null, List.of("password")), nullValue());
        assertThat(LogMasking.maskBody("", List.of("password")), equalTo(""));
        assertThat(LogMasking.maskBody("{\"password\":\"x\"}", List.of()), equalTo("{\"password\":\"x\"}"));
    }

    @Test
    @DisplayName("sanitize escapa CR/LF y convierte null en vacío")
    void sanitizesLineBreaks() {
        assertThat(LogMasking.sanitize("a\r\nb"), equalTo("a\\r\\nb"));
        assertThat(LogMasking.sanitize(null), equalTo(""));
    }
}
