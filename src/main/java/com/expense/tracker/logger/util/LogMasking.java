package com.expense.tracker.logger.util;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * Enmascarado y saneo de lo que sale por las líneas de log. Nunca modifica el request ni la response
 * reales: trabaja sobre copias en texto.
 */
public final class LogMasking {

    private LogMasking() {
    }

    /**
     * Arma la representación de los headers para el log, con los valores de {@code maskedHeaders}
     * reemplazados por {@link LogConstants#MASK} (comparación case-insensitive).
     *
     * <p>Formato: {@code {Nombre="valor", Multi=["v1", "v2"]}}. Las comillas distinguen un header repetido
     * de un único valor que contiene comas.</p>
     */
    public static String formatHeaders(Map<String, List<String>> headers, Collection<String> maskedHeaders) {
        Set<String> masked = maskedHeaders.stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        return headers.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + formatValues(
                        masked.contains(entry.getKey().toLowerCase(Locale.ROOT)) ? List.of(LogConstants.MASK) : entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String formatValues(List<String> values) {
        if (values.size() == 1) {
            return quote(values.getFirst());
        }
        return values.stream().map(LogMasking::quote).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    /**
     * Enmascara en un body JSON serializado el valor de las claves indicadas (string entrecomillado o escalar).
     * Es best-effort sobre texto, sin Jackson, y conserva el espaciado original: {@code "password" : "x"}
     * pasa a {@code "password" : "*****"}.
     */
    public static @Nullable String maskBody(@Nullable String body, Collection<String> fields) {
        if (body == null || body.isEmpty() || fields.isEmpty()) {
            return body;
        }
        var masked = body;
        for (String field : fields) {
            if (field == null || field.isBlank()) {
                continue;
            }
            var pattern = Pattern.compile(
                    "(\"" + Pattern.quote(field.trim()) + "\"\\s*:\\s*)(\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\]\\s]+)");
            masked = pattern.matcher(masked).replaceAll(match -> match.group(1) + "\"" + LogConstants.MASK + "\"");
        }
        return masked;
    }

    /**
     * Escapa CR/LF para que un valor controlado por el cliente no pueda inyectar líneas falsas en el log (CWE-117).
     */
    public static String sanitize(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
