package com.expense.tracker.logger.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CachedBodyRequestWrapperTest {

    @Test
    @DisplayName("re-sirve el body completo por stream y por reader")
    void replaysBody() throws Exception {
        var request = new MockHttpServletRequest();
        request.setContent("{\"a\":\"ñ\"}".getBytes(StandardCharsets.UTF_8));
        request.setCharacterEncoding("UTF-8");
        var wrapper = new CachedBodyRequestWrapper(request);

        var stream = wrapper.getInputStream();
        var buffer = new byte[64];
        var read = stream.read(buffer, 0, buffer.length);

        assertAll(
                () -> assertThat(new String(buffer, 0, read, StandardCharsets.UTF_8), equalTo("{\"a\":\"ñ\"}")),
                () -> assertThat(stream.isFinished(), is(true)),
                () -> assertThat(stream.isReady(), is(true)),
                () -> assertThat(wrapper.getInputStream().read(), equalTo((int) '{')),
                () -> assertThat(wrapper.getReader().readLine(), equalTo("{\"a\":\"ñ\"}")),
                () -> assertThrows(UnsupportedOperationException.class, () -> stream.setReadListener(null)));
    }

    @Test
    @DisplayName("sin encoding declarado lee en UTF-8")
    void defaultsToUtf8() throws Exception {
        var request = new MockHttpServletRequest();
        request.setContent("ñ".getBytes(StandardCharsets.UTF_8));

        assertThat(new CachedBodyRequestWrapper(request).getReader().readLine(), equalTo("ñ"));
    }
}
