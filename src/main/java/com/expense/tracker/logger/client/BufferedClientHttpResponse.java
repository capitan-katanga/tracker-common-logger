package com.expense.tracker.logger.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Response con el body bufferizado en memoria: {@link LoggingClientInterceptor} consume el stream original
 * para loguearlo y este wrapper se lo re-sirve intacto al caller.
 */
class BufferedClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse delegate;
    private final byte[] body;

    BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
        this.delegate = delegate;
        this.body = body;
    }

    @Override
    public HttpStatusCode getStatusCode() throws IOException {
        return delegate.getStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
        return delegate.getStatusText();
    }

    @Override
    public HttpHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public InputStream getBody() {
        return new ByteArrayInputStream(body);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
