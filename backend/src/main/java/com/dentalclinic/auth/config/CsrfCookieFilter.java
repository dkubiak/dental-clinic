package com.dentalclinic.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the deferred {@link CsrfToken} to actually resolve on every request, which is what makes
 * {@link SecurityConfig}'s {@code CookieCsrfTokenRepository} write the {@code XSRF-TOKEN} cookie.
 *
 * <p>Without this, the cookie is never sent to the browser: Spring Security 6's default {@code
 * CsrfTokenRequestAttributeHandler} wraps the token as a lazily-resolved supplier, and nothing in a
 * pure JSON REST API (no server-rendered view referencing {@code ${_csrf.token}}) ever calls {@code
 * .getToken()} to trigger that resolution — so the Angular frontend's built-in XSRF interceptor
 * (which reads the {@code XSRF-TOKEN} cookie and sends it back as {@code X-XSRF-TOKEN},
 * `provideHttpClient()`'s default behavior) has no cookie to read, and every mutating request to a
 * non-pre-auth endpoint (logout, and every Phase 4/5 admin action) would 403. This is the standard
 * fix documented for Spring Security + SPA CSRF setups.
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
