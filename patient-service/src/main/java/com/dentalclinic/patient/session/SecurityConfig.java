package com.dentalclinic.patient.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Security baseline for {@code patient-service} (T020). Authorization itself (role-scoped
 * {@code @PreAuthorize} rules per contracts/patient-api.yaml / rbac-policy.md) is each endpoint's
 * own concern (US1+, T037 onward) — this class only wires the mechanism every endpoint relies on:
 * resolving the caller's identity/role from auth-service's shared session ({@link
 * SessionAuthenticationFilter}), and CSRF protection for the same double-submit XSRF-TOKEN cookie
 * auth-service's {@code SecurityConfig}/{@code CsrfCookieFilter} already establish (same
 * origin/site via nginx, so the one cookie covers both services' mutating endpoints).
 *
 * <p>{@code SessionCreationPolicy.STATELESS}: this service never creates or persists its own {@code
 * HttpSession} — {@link SessionAuthenticationFilter} resolves authentication fresh on every request
 * directly from auth-service's session table, so there is nothing for Spring Security's own
 * session-management machinery to manage here (research.md #7 — "reads, never writes/invalidates").
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] PERMIT_ALL_PATHS = {"/actuator/health/**"};

  private final SessionAuthenticationFilter sessionAuthenticationFilter;

  public SecurityConfig(SessionAuthenticationFilter sessionAuthenticationFilter) {
    this.sessionAuthenticationFilter = sessionAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    // Spring Security's default CsrfAuthenticationStrategy deletes the
                    // XSRF-TOKEN cookie whenever it sees a "new authentication" during a
                    // request, to protect against session fixation. SessionAuthenticationFilter
                    // authenticates fresh from JDBC on EVERY request (this service never
                    // persists its own SecurityContext, research.md #7) — Spring Security can't
                    // tell that apart from "the user just logged in this request", so the
                    // default strategy fired (and silently deleted the cookie) on every single
                    // request, breaking every mutating call after the first (T063 finding). No
                    // fixation risk exists here to protect against in the first place — this
                    // service never creates an HttpSession — so disabling it is correct, not
                    // just a workaround.
                    .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        // Same anchor point/reasoning as auth-service's own SecurityConfig:
        // BasicAuthenticationFilter
        // itself is not present in this chain (no .httpBasic()), but Spring Security's
        // FilterOrderRegistration still knows its position, well after CsrfFilter so the CsrfToken
        // request attribute it resolves already exists (T020/CsrfCookieFilter javadoc).
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(PERMIT_ALL_PATHS)
                    .permitAll()
                    .anyRequest()
                    .authenticated());

    return http.build();
  }
}
