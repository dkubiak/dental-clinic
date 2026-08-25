package com.dentalclinic.patient.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request by reading (never writing/invalidating) the *same* Spring Session JDBC
 * tables {@code auth-service} writes (research.md #7, plan.md Risk Tier & Availability) — the
 * identical mechanism that already lets {@code auth-service}'s own ≥2 replicas share sessions,
 * extended to a second service's DB role ({@code patient_service_app}, grant: {@code SELECT,
 * UPDATE} only — never {@code INSERT}/{@code DELETE}; session lifecycle stays exclusively
 * auth-service's responsibility). No new cross-service auth-token exchange is introduced.
 *
 * <p>Deserializes the {@code SPRING_SECURITY_CONTEXT} session attribute with plain JDK
 * serialization, matching Spring Session JDBC's own default attribute-serialization strategy
 * (auth-service's {@code SessionConfig} configures no custom serializer) — the same trust boundary
 * auth-service's own {@code JdbcIndexedSessionRepository} already relies on for this exact byte
 * content; this filter does not deserialize any input an external caller controls directly, only
 * bytes {@code auth-service} itself wrote to this shared, internal table.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  private static final String SESSION_COOKIE_NAME = "SESSION";
  private static final String SECURITY_CONTEXT_ATTRIBUTE_NAME = "SPRING_SECURITY_CONTEXT";

  private final JdbcTemplate jdbcTemplate;

  public SessionAuthenticationFilter(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    readSessionId(request)
        .flatMap(this::resolveAuthentication)
        .ifPresent(
            authentication -> {
              SecurityContext context = SecurityContextHolder.createEmptyContext();
              context.setAuthentication(authentication);
              SecurityContextHolder.setContext(context);
            });
    filterChain.doFilter(request, response);
  }

  private Optional<String> readSessionId(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
        try {
          return Optional.of(
              new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
          return Optional.empty(); // malformed cookie value — treat as no session
        }
      }
    }
    return Optional.empty();
  }

  private Optional<Authentication> resolveAuthentication(String sessionId) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT primary_id, expiry_time FROM spring_session WHERE session_id = ?", sessionId);
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Object> row = rows.get(0);
    String primaryId = (String) row.get("primary_id");
    long expiryTime = ((Number) row.get("expiry_time")).longValue();
    if (expiryTime < System.currentTimeMillis()) {
      return Optional.empty(); // expired — auth-service's own retention/expiry sweep owns cleanup
    }

    byte[] securityContextBytes;
    try {
      securityContextBytes =
          jdbcTemplate.queryForObject(
              "SELECT attribute_bytes FROM spring_session_attributes"
                  + " WHERE session_primary_id = ? AND attribute_name = ?",
              byte[].class,
              primaryId,
              SECURITY_CONTEXT_ATTRIBUTE_NAME);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty(); // session exists but was never authenticated (shouldn't happen)
    }
    if (securityContextBytes == null) {
      return Optional.empty();
    }

    // Bump last_access_time on read, matching Spring Session JDBC's own read-path behavior
    // (research.md #7) — the UPDATE half of this role's SELECT, UPDATE grant.
    jdbcTemplate.update(
        "UPDATE spring_session SET last_access_time = ? WHERE primary_id = ?",
        System.currentTimeMillis(),
        primaryId);

    return Optional.ofNullable(
        deserializeSecurityContext(securityContextBytes).getAuthentication());
  }

  private SecurityContext deserializeSecurityContext(byte[] bytes) {
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return (SecurityContext) ois.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new IllegalStateException("Failed to deserialize SPRING_SECURITY_CONTEXT", e);
    }
  }
}
