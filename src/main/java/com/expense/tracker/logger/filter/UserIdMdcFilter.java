package com.expense.tracker.logger.filter;

import com.expense.tracker.logger.util.LogConstants;
import com.expense.tracker.logger.util.LogMasking;
import org.slf4j.MDC;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Deja en el MDC el {@code userId} del usuario autenticado (el nombre del {@code Authentication}, que con un
 * JWT de Keycloak es el claim {@code sub}). Corre después de la cadena de Spring Security, que es la que
 * puebla el {@code SecurityContext}; solo se registra si Spring Security está en el classpath.
 *
 * <p>No limpia la clave al terminar: la limpia {@link HttpLoggingFilter}, que la envuelve, para que el
 * {@code userId} llegue a la línea de RESPONSE.</p>
 */
public class UserIdMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            MDC.put(LogConstants.MDC_USER_ID, LogMasking.sanitize(authentication.getName()));
        }
        filterChain.doFilter(request, response);
    }
}
