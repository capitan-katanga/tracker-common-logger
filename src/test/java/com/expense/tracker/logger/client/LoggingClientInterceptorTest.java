package com.expense.tracker.logger.client;

import com.expense.tracker.logger.LogCapture;
import com.expense.tracker.logger.util.LogConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LoggingClientInterceptorTest {

    private MockRestServiceServer server;
    private RestClient restClient;
    private LogCapture logs;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder()
                .baseUrl("http://transactional")
                .requestInterceptor(new LoggingClientInterceptor(List.of("Authorization"), List.of("password"), 4096));
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        logs = LogCapture.of(LoggingClientInterceptor.class);
    }

    @AfterEach
    void tearDown() {
        logs.close();
        MDC.clear();
    }

    @Test
    @DisplayName("propaga el correlationId del MDC y loguea request y response enmascarados")
    void propagatesCorrelationIdAndLogs() {
        MDC.put(LogConstants.MDC_CORRELATION_ID, "abc");
        server.expect(requestTo("http://transactional/api/v1/transactions"))
                .andExpect(header(LogConstants.CORRELATION_ID_HEADER, "abc"))
                .andRespond(withSuccess("{\"id\":1,\"token\":\"t\",\"password\":\"p\"}", MediaType.APPLICATION_JSON));

        var body = restClient.post().uri("/api/v1/transactions")
                .header("Authorization", "Bearer secreto")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"password\":\"s3cr3t\"}")
                .retrieve()
                .body(String.class);

        server.verify();
        var messages = logs.messages();
        assertAll(
                () -> assertThat(body, equalTo("{\"id\":1,\"token\":\"t\",\"password\":\"p\"}")),
                () -> assertThat(messages, hasSize(2)),
                () -> assertThat(messages.get(0), startsWith("CLIENT REQUEST  --> POST http://transactional/api/v1/transactions")),
                () -> assertThat(messages.get(0), containsString("Authorization=\"*****\"")),
                () -> assertThat(messages.get(0), containsString("X-Correlation-Id=\"abc\"")),
                () -> assertThat(messages.get(0), containsString("body={\"password\":\"*****\"}")),
                () -> assertThat(messages.get(1), startsWith("CLIENT RESPONSE <-- 200 (")),
                () -> assertThat(messages.get(1), containsString("body={\"id\":1,\"token\":\"t\",\"password\":\"*****\"}")),
                () -> assertThat(messages.get(1), not(containsString("\"p\""))));
    }

    @Test
    @DisplayName("sin correlationId en el MDC no agrega el header y respeta uno explícito")
    void keepsExplicitOrMissingCorrelationId() {
        server.expect(requestTo("http://transactional/a"))
                .andExpect(headerDoesNotExist(LogConstants.CORRELATION_ID_HEADER))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo("http://transactional/b"))
                .andExpect(header(LogConstants.CORRELATION_ID_HEADER, "explicito"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        restClient.get().uri("/a").retrieve().toBodilessEntity();
        MDC.put(LogConstants.MDC_CORRELATION_ID, "del-mdc");
        restClient.get().uri("/b").header(LogConstants.CORRELATION_ID_HEADER, "explicito").retrieve().toBodilessEntity();

        server.verify();
        assertThat(logs.messages().get(1), startsWith("CLIENT RESPONSE <-- 204 ("));
    }

    @Test
    @DisplayName("con el logger por encima de INFO igual ejecuta la llamada sin loguear")
    void skipsLinesWhenInfoDisabled() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingClientInterceptor.class);
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
        server.expect(requestTo("http://transactional/a")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));
        try {
            assertThat(restClient.get().uri("/a").retrieve().body(String.class), equalTo("ok"));
        } finally {
            logger.setLevel(null);
        }

        assertThat(logs.messages(), empty());
    }

    @Test
    @DisplayName("un error de I/O se loguea como CLIENT ERROR y se relanza")
    void logsIoErrors() {
        server.expect(requestTo("http://transactional/a")).andRespond(request -> {
            throw new SocketTimeoutException("timeout");
        });

        assertThrows(ResourceAccessException.class, () -> restClient.get().uri("/a").retrieve().toBodilessEntity());

        assertThat(logs.messages().getLast(), startsWith("CLIENT ERROR    <-- GET http://transactional/a | error="));
        assertThat(logs.messages().getLast(), containsString("timeout"));
    }

    @Test
    @DisplayName("la response bufferizada delega status, headers y close")
    void bufferedResponseDelegates() throws Exception {
        var delegate = new MockClientHttpResponse(new byte[0], HttpStatus.CREATED);
        delegate.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        ClientHttpResponse buffered = new BufferedClientHttpResponse(delegate, "hola".getBytes(StandardCharsets.UTF_8));

        assertAll(
                () -> assertThat(buffered.getStatusCode(), equalTo(HttpStatus.CREATED)),
                () -> assertThat(buffered.getStatusText(), equalTo("Created")),
                () -> assertThat(buffered.getHeaders().getContentType(), equalTo(MediaType.TEXT_PLAIN)),
                () -> assertThat(new String(buffered.getBody().readAllBytes(), StandardCharsets.UTF_8), equalTo("hola")));
        buffered.close();
    }
}
