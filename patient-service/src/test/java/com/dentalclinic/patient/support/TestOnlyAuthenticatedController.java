package com.dentalclinic.patient.support;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mirrors {@code backend}'s {@code TestOnlyAdminController} pattern — a minimal fixture proving the
 * general session-authentication pipeline (T020's {@code SessionAuthenticationFilter} + {@code
 * SecurityConfig}'s {@code .anyRequest().authenticated()}) end-to-end, independent of which
 * business feature ships the first real {@code /patients/**} endpoint (US1, T037). Lives only on
 * the test classpath (src/test/java), never in a production build. See
 * SessionAuthenticationFilterTest (T019).
 */
@RestController
public class TestOnlyAuthenticatedController {

  @GetMapping("/test-support/authenticated")
  public ResponseEntity<String> authenticated() {
    return ResponseEntity.ok("ok");
  }
}
