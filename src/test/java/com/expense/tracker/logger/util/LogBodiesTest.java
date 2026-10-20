package com.expense.tracker.logger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class LogBodiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/plain", "application/xml", "application/x-www-form-urlencoded",
            "application/problem+json", "application/atom+xml", "application/json;charset=UTF-8"})
    @DisplayName("isTextual acepta texto, JSON, XML y forms")
    void textualTypes(String type) {
        assertThat(LogBodies.isTextual(MediaType.parseMediaType(type)), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"multipart/form-data", "image/png", "application/octet-stream", "application/pdf"})
    @DisplayName("isTextual rechaza multipart y binarios")
    void binaryTypes(String type) {
        assertThat(LogBodies.isTextual(MediaType.parseMediaType(type)), is(false));
    }

    @Test
    @DisplayName("isTextual asume texto sin content-type")
    void nullTypeIsTextual() {
        assertThat(LogBodies.isTextual(null), is(true));
    }

    @Test
    @DisplayName("parseContentType tolera nulos, blancos y valores inválidos")
    void parsesContentType() {
        assertThat(LogBodies.parseContentType(null), nullValue());
        assertThat(LogBodies.parseContentType(" "), nullValue());
        assertThat(LogBodies.parseContentType("no es un tipo"), nullValue());
        assertThat(LogBodies.parseContentType("application/json"), equalTo(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("render devuelve vacío sin body y resume los binarios")
    void rendersEmptyAndBinary() {
        assertThat(LogBodies.render(new byte[0], MediaType.APPLICATION_JSON, 10, List.of()), equalTo(""));
        assertThat(LogBodies.render(new byte[3], MediaType.IMAGE_PNG, 10, List.of()), equalTo("<image/png, 3 bytes omitido>"));
    }

    @Test
    @DisplayName("render enmascara, trunca y respeta el charset declarado")
    void rendersTextual() {
        var json = "{\"password\":\"x\",\"amount\":123456789}".getBytes(StandardCharsets.UTF_8);
        assertThat(LogBodies.render(json, MediaType.APPLICATION_JSON, 200, List.of("password")),
                equalTo("{\"password\":\"*****\",\"amount\":123456789}"));
        assertThat(LogBodies.render(json, MediaType.APPLICATION_JSON, 5, List.of()),
                equalTo("{\"pas...<truncado, 35 bytes>"));

        var latin = "ñandú\n".getBytes(StandardCharsets.ISO_8859_1);
        assertThat(LogBodies.render(latin, MediaType.parseMediaType("text/plain;charset=ISO-8859-1"), 100, List.of()),
                equalTo("ñandú\\n"));
    }
}
