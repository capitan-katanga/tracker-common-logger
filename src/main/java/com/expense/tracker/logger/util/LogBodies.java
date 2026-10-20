package com.expense.tracker.logger.util;

import org.jspecify.annotations.Nullable;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Convierte un body HTTP (bytes) en el texto que va al log: solo se loguean contenidos textuales,
 * enmascarados y truncados; un binario o un multipart (p. ej. la imagen de un comprobante) se resume
 * con su tipo y tamaño.
 */
public final class LogBodies {

    private LogBodies() {
    }

    /**
     * Indica si el content-type es texto legible: {@code text/*}, JSON, XML (y sus variantes {@code +json}/{@code +xml})
     * o form-urlencoded. Sin content-type se asume texto.
     */
    public static boolean isTextual(@Nullable MediaType contentType) {
        if (contentType == null) {
            return true;
        }
        var subtype = contentType.getSubtype();
        return "text".equals(contentType.getType())
                || MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || MediaType.APPLICATION_XML.isCompatibleWith(contentType)
                || MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)
                || subtype.endsWith("+json")
                || subtype.endsWith("+xml");
    }

    /**
     * Parsea un content-type tolerando valores nulos o mal formados (devuelve {@code null}).
     */
    public static @Nullable MediaType parseContentType(@Nullable String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException _) {
            return null;
        }
    }

    /**
     * Arma el texto del body para el log.
     *
     * @param body          bytes del body (vacío si no hubo)
     * @param contentType   tipo del body
     * @param maxLength     máximo de caracteres a loguear; lo que sobra se corta con una marca
     * @param maskedFields  claves JSON a enmascarar
     * @return el body listo para el log, o cadena vacía si no hubo body
     */
    public static String render(byte[] body, @Nullable MediaType contentType, int maxLength, Collection<String> maskedFields) {
        if (body.length == 0) {
            return "";
        }
        if (!isTextual(contentType)) {
            return "<" + contentType + ", " + body.length + " bytes omitido>";
        }
        var text = LogMasking.maskBody(new String(body, charsetOf(contentType)), maskedFields);
        if (text != null && text.length() > maxLength) {
            text = text.substring(0, maxLength) + "...<truncado, " + body.length + " bytes>";
        }
        return LogMasking.sanitize(text);
    }

    private static Charset charsetOf(@Nullable MediaType contentType) {
        return contentType != null && contentType.getCharset() != null ? contentType.getCharset() : StandardCharsets.UTF_8;
    }
}
