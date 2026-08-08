package com.yliu22520.iotota.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class SessionLifetimeFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_AT = SessionLifetimeFilter.class.getName() + ".authenticatedAt";
    private static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(8);

    private final Clock clock;

    public SessionLifetimeFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (session != null && authentication != null && authentication.isAuthenticated()) {
            Object authenticatedAt = session.getAttribute(AUTHENTICATED_AT);
            if (authenticatedAt instanceof String value && isExpired(value)) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session 已超过绝对有效期");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isExpired(String authenticatedAt) {
        try {
            return !Instant.parse(authenticatedAt).plus(ABSOLUTE_LIFETIME).isAfter(Instant.now(clock));
        } catch (RuntimeException ignored) {
            return true;
        }
    }
}
