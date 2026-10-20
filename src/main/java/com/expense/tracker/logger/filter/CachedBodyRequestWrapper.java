package com.expense.tracker.logger.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Request que lee el body completo al construirse y lo re-sirve en cada {@code getInputStream()}/{@code getReader()}.
 * Así el filtro loguea el body ANTES de que corra el controller, y el controller lo sigue leyendo entero.
 *
 * <p>Solo se usa con bodies textuales que no sean form-urlencoded: un multipart o un form se leen por
 * {@code getParameter}/{@code getPart}, que no pasan por este stream.</p>
 */
class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    byte[] getBody() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        var stream = new ByteArrayInputStream(body);
        return new ServletInputStream() {

            @Override
            public boolean isFinished() {
                return stream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("Lectura asíncrona no soportada");
            }

            @Override
            public int read() {
                return stream.read();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                return stream.read(buffer, offset, length);
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        var encoding = getCharacterEncoding();
        var charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }
}
