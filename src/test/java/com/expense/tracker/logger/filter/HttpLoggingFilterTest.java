package com.expense.tracker.logger.filter;

import com.expense.tracker.logger.LogCapture;
import com.expense.tracker.logger.TestProperties;
import com.expense.tracker.logger.util.LogConstants;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import jakarta.servlet.ServletException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpLoggingFilterTest {

    private MockMvc mockMvc;
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .addFilters(new HttpLoggingFilter(TestProperties.withMaxBodyLength(64)))
                .build();
        logs = LogCapture.of(HttpLoggingFilter.class);
    }

    @AfterEach
    void tearDown() {
        logs.close();
        MDC.clear();
    }

    @Test
    @DisplayName("propaga el X-Correlation-Id recibido y devuelve el X-Trace-Id del MDC")
    void propagatesCorrelationAndTraceIds() throws Exception {
        MDC.put(LogConstants.MDC_TRACE_ID, "trace-1");

        mockMvc.perform(get("/api/ping").header(LogConstants.CORRELATION_ID_HEADER, "abc"))
                .andExpect(status().isOk())
                .andExpect(header().string(LogConstants.CORRELATION_ID_HEADER, "abc"))
                .andExpect(header().string(LogConstants.TRACE_ID_HEADER, "trace-1"))
                .andExpect(content().string("abc"));
    }

    @Test
    @DisplayName("genera un correlationId si no viene y no devuelve X-Trace-Id sin traza")
    void generatesCorrelationId() throws Exception {
        var result = mockMvc.perform(get("/api/ping"))
                .andExpect(header().doesNotExist(LogConstants.TRACE_ID_HEADER))
                .andReturn();

        var correlationId = result.getResponse().getHeader(LogConstants.CORRELATION_ID_HEADER);
        assertAll(
                () -> assertThat(UUID.fromString(correlationId).toString(), equalTo(correlationId)),
                () -> assertThat(result.getResponse().getContentAsString(), equalTo(correlationId)));
    }

    @Test
    @DisplayName("un X-Correlation-Id en blanco se reemplaza por uno generado")
    void blankCorrelationIdIsReplaced() throws Exception {
        var result = mockMvc.perform(get("/api/ping").header(LogConstants.CORRELATION_ID_HEADER, " ")).andReturn();

        assertThat(result.getResponse().getHeader(LogConstants.CORRELATION_ID_HEADER).length(), equalTo(36));
    }

    @Test
    @DisplayName("loguea request y response con headers y body enmascarados y key-values para Loki")
    void logsRequestAndResponse() throws Exception {
        mockMvc.perform(post("/api/echo?page=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer secreto")
                        .content("{\"user\":\"jdoe\",\"password\":\"s3cr3t\"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"user\":\"jdoe\",\"password\":\"s3cr3t\"}"));

        var messages = logs.messages();
        assertAll(
                () -> assertThat(messages, hasSize(2)),
                () -> assertThat(messages.get(0), startsWith("REQUEST  --> POST /api/echo?page=1 | headers={")),
                () -> assertThat(messages.get(0), containsString("Authorization=\"*****\"")),
                () -> assertThat(messages.get(0), not(containsString("secreto"))),
                () -> assertThat(messages.get(0), containsString("body={\"user\":\"jdoe\",\"password\":\"*****\"}")),
                () -> assertThat(messages.get(1), startsWith("RESPONSE <-- 200 (")),
                () -> assertThat(messages.get(1), containsString("ms) POST /api/echo?page=1 | headers={")),
                () -> assertThat(messages.get(1), containsString("body={\"user\":\"jdoe\",\"password\":\"*****\"}")),
                () -> assertThat(keyValues(logs.events().get(1)), hasEntry("http.method", "POST")),
                () -> assertThat(keyValues(logs.events().get(1)), hasEntry("http.uri", "/api/echo?page=1")),
                () -> assertThat(keyValues(logs.events().get(1)), hasEntry("http.status", "200")),
                () -> assertThat(keyValues(logs.events().get(1)), hasKey("duration_ms")));
    }

    @Test
    @DisplayName("trunca los bodies largos")
    void truncatesLongBodies() throws Exception {
        var longValue = "x".repeat(200);

        mockMvc.perform(post("/api/echo").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + longValue + "\"}"))
                .andExpect(status().isOk());

        assertThat(logs.messages().getFirst(), containsString("...<truncado, 211 bytes>"));
    }

    @Test
    @DisplayName("no loguea los bytes de un multipart y el controller recibe el archivo")
    void omitsMultipartBody() throws Exception {
        var file = new MockMultipartFile("file", "ticket.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));

        assertThat(logs.messages().getFirst(), containsString("multipart/form-data"));
        assertThat(logs.messages().getFirst(), containsString("omitido>"));
    }

    @Test
    @DisplayName("un form-urlencoded no se consume: el controller sigue leyendo sus parámetros")
    void keepsFormParameters() throws Exception {
        mockMvc.perform(post("/api/form").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("name=ana"))
                .andExpect(status().isOk())
                .andExpect(content().string("ana"));
    }

    @Test
    @DisplayName("no loguea rutas de infraestructura")
    void skipsExcludedPaths() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(LogConstants.CORRELATION_ID_HEADER));

        assertThat(logs.messages(), empty());
    }

    @Test
    @DisplayName("ante una excepción loguea 500 y la deja propagar")
    void logsServerErrorOnException() {
        assertThrows(ServletException.class, () -> mockMvc.perform(get("/api/boom")));

        assertThat(logs.messages(), hasItem(startsWith("RESPONSE <-- 500 (")));
    }

    @Test
    @DisplayName("limpia correlationId y userId del MDC al terminar")
    void clearsMdc() throws Exception {
        mockMvc.perform(get("/api/user"));

        assertAll(
                () -> assertThat(MDC.get(LogConstants.MDC_CORRELATION_ID), nullValue()),
                () -> assertThat(MDC.get(LogConstants.MDC_USER_ID), nullValue()));
    }

    @Test
    @DisplayName("con el logger por encima de INFO no arma las líneas")
    void skipsWhenInfoDisabled() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(HttpLoggingFilter.class);
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
        try {
            mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        } finally {
            logger.setLevel(null);
        }

        assertThat(logs.messages(), empty());
    }

    private static Map<String, String> keyValues(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        return pairs.stream().collect(Collectors.toMap(kv -> kv.key, kv -> String.valueOf(kv.value)));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/ping")
        String ping() {
            return MDC.get(LogConstants.MDC_CORRELATION_ID);
        }

        @PostMapping("/api/echo")
        Map<String, Object> echo(@RequestBody Map<String, Object> body) {
            return body;
        }

        @PostMapping("/api/upload")
        String upload(@RequestPart("file") MultipartFile file) {
            return String.valueOf(file.getSize());
        }

        @PostMapping("/api/form")
        String form(@RequestParam("name") String name) {
            return name;
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }

        @GetMapping("/api/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/api/user")
        String user() {
            MDC.put(LogConstants.MDC_USER_ID, "user-1");
            return "ok";
        }
    }
}
