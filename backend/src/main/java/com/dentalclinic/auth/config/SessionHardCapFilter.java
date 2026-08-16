package com.dentalclinic.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the 8-hour hard session cap (FR-012, spec Assumptions) independent of activity — Spring
 * Session's own {@code maxInactiveInterval} only covers idle timeout (T027 SessionConfig), not an
 * absolute cap, so this is implemented as an explicit filter.
 *
 * <p>{@link #AUTHENTICATED_AT_ATTRIBUTE} MUST be set on the session at the moment authentication
 * completes (AuthService, after successful MFA verification — Phase 3, T040/T045) for this check to
 * have anything to compare against; a session with no such attribute is treated as
 * pre-authentication and passed through untouched (auth endpoints permitAll — see SecurityConfig).
 */
@Component
public class SessionHardCapFilter extends OncePerRequestFilter {

  public static final String AUTHENTICATED_AT_ATTRIBUTE = "auth.authenticatedAt";
  private static final Duration HARD_CAP = Duration.ofHours(8);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    if (session != null) {
      Object authenticatedAt = session.getAttribute(AUTHENTICATED_AT_ATTRIBUTE);
      if (authenticatedAt instanceof Instant instant
          && Instant.now().isAfter(instant.plus(HARD_CAP))) {
        session.invalidate();
      }
    }
    filterChain.doFilter(request, response);
  }
}
