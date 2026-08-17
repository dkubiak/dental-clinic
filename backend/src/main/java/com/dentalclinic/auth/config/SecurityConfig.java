package com.dentalclinic.auth.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security baseline (T026, research.md #6). Role/endpoint authorization itself is NOT configured
 * here — that is T028 (404-not-403 mapping) and T047's {@code @PreAuthorize} rules per
 * contracts/rbac-policy.md; this class only sets up the mechanisms every endpoint relies on:
 * password hashing, session-cookie-based auth, CSRF protection for that cookie, and CORS.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // required for @PreAuthorize (T047) to be evaluated at all
public class SecurityConfig {

  private final CorsProperties corsProperties;
  private final SessionHardCapFilter sessionHardCapFilter;

  public SecurityConfig(CorsProperties corsProperties, SessionHardCapFilter sessionHardCapFilter) {
    this.corsProperties = corsProperties;
    this.sessionHardCapFilter = sessionHardCapFilter;
  }

  /**
   * Argon2id (research.md #6) — OWASP's current recommended default, memory-hard, chosen over
   * bcrypt for the sensitivity of what this login gates (special-category patient data behind it,
   * Principle II).
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    // Parameters per OWASP Password Storage Cheat Sheet Argon2id guidance: 19 MiB memory, 2
    // iterations, 1 degree of parallelism, 32-byte hash, 16-byte salt.
    return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
  }

  /**
   * Used explicitly (rather than left to Spring Security's internal default) so {@link
   * com.dentalclinic.auth.api.SessionEstablisher} can inject the same instance to persist the
   * {@link org.springframework.security.core.context.SecurityContext} into the HTTP session after
   * MFA verification succeeds (T045) — saving under the standard {@code SPRING_SECURITY_CONTEXT}
   * session attribute is also what lets Spring Session JDBC's principal-name index (T075/T075a)
   * resolve correctly.
   */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /**
   * Endpoints reachable before any session/CSRF-cookie relationship exists (the pre-auth login flow
   * and self-service password reset — all {@code security: []} in contracts/auth-api.yaml). A
   * browser's very first request to the app is one of these, so it cannot yet be carrying a CSRF
   * cookie to echo back; requiring one here would make login impossible, not more secure — there is
   * no ambient authenticated session for a forged cross-site request to ride on until after MFA
   * succeeds, which is exactly where CSRF protection resumes (SessionEstablisher, T045).
   */
  private static final String[] PRE_AUTH_PATHS = {
    "/auth/login",
    "/auth/mfa/verify",
    "/auth/password-reset/request",
    "/auth/password-reset/confirm"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler)
                    .ignoringRequestMatchers(PRE_AUTH_PATHS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .securityContext(context -> context.securityContextRepository(securityContextRepository()))
        .addFilterAfter(sessionHardCapFilter, SecurityContextHolderFilter.class)
        // Anchored at BasicAuthenticationFilter's position (Spring Security's own documented
        // placement for this pattern — docs.spring.io "CSRF and Single Page Applications"), which
        // is well after CsrfFilter (so the CsrfToken request attribute it resolves already
        // exists) and close to the end of the chain, minimizing how many other filters run
        // between this one and the controller — forces the XSRF-TOKEN cookie to actually be
        // written, see CsrfCookieFilter javadoc. BasicAuthenticationFilter itself is not present
        // in this chain (no .httpBasic()); Spring Security's FilterOrderRegistration still knows
        // its position for anchoring purposes.
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            authorize ->
                authorize.requestMatchers(PRE_AUTH_PATHS).permitAll().anyRequest().authenticated());

    return http.build();
  }

  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
    configuration.setAllowCredentials(
        true); // session cookie must travel with cross-origin requests

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
