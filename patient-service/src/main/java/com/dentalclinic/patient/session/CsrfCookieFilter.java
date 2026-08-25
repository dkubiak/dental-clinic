package com.dentalclinic.patient.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mirrors {@code backend}'s {@code com.dentalclinic.auth.config.CsrfCookieFilter} exactly (same
 * gap, same fix — discovered as a live gap while running 002-patient-records' quickstart
 * validation, T063, since every existing patient-service integration test uses {@code
 * PostgresIntegrationTestBase}'s fixed test-only CSRF cookie/header pair, bypassing the real
 * deferred-token issuance path this filter fixes). Forces the deferred {@link CsrfToken} to
 * actually resolve on every request, which is what makes {@link SecurityConfig}'s {@code
 * CookieCsrfTokenRepository} write the {@code XSRF-TOKEN} cookie — without this, the cookie is
 * never sent to the browser (Spring Security 6+'s default {@code CsrfTokenRequestAttributeHandler}
 * wraps the token as a lazily-resolved supplier, and nothing in a pure JSON REST API ever calls
 * {@code .getToken()} to trigger that resolution), so Angular's built-in XSRF interceptor has no
 * cookie to read and every mutating request would fail.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken != null) {
      csrfToken.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
