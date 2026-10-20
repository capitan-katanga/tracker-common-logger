package com.expense.tracker.logger.filter;

import com.expense.tracker.logger.util.LogConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

class UserIdMdcFilterTest {

    private final UserIdMdcFilter filter = new UserIdMdcFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("pone el userId del usuario autenticado en el MDC")
    void putsAuthenticatedUser() throws Exception {
        var token = new TestingAuthenticationToken("user-123", "n/a", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(token);

        assertThat(userIdSeenByChain(), equalTo("user-123"));
    }

    @Test
    @DisplayName("no pone userId sin autenticación, anónimo o no autenticado")
    void skipsMissingOrAnonymousUser() throws Exception {
        assertThat(userIdSeenByChain(), nullValue());

        Authentication anonymous = new AnonymousAuthenticationToken("key", "anon",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        assertThat(userIdSeenByChain(), nullValue());

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user-123", "n/a"));
        assertThat(userIdSeenByChain(), nullValue());
    }

    private String userIdSeenByChain() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        var chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                seen.set(MDC.get(LogConstants.MDC_USER_ID));
            }
        });
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
        return seen.get();
    }
}
