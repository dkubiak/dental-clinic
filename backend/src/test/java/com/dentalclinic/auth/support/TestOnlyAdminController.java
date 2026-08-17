package com.dentalclinic.auth.support;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only fixture proving the general RBAC enforcement pipeline (T047's {@code @PreAuthorize}
 * mechanism + T028's 404-not-403 mapping) end-to-end, independent of which business feature is the
 * first to actually ship an admin-only endpoint (that's AccountController/AuditLogController,
 * US2/US3 — Phase 4/5). Lives only on the test classpath (src/test/java), never in a production
 * build. See RbacEnforcementIntegrationTest (T036).
 */
@RestController
public class TestOnlyAdminController {

  @GetMapping("/test-support/admin-only")
  @PreAuthorize("hasRole('ADMINISTRATOR')")
  public ResponseEntity<String> adminOnly() {
    return ResponseEntity.ok("ok");
  }

  /**
   * Stands in for a future feature's patient-data endpoint (patient records, scheduling, billing —
   * none exist yet in this repo) so this feature can already enforce and regression-test
   * contracts/rbac-policy.md rule 3 ("No implicit admin clinical access") — see
   * AdministratorNoClinicalAccessTest (T071).
   */
  @GetMapping("/test-support/clinical-data-only")
  @PreAuthorize("hasAnyRole('RECEPTION', 'DOCTOR')")
  public ResponseEntity<String> clinicalDataOnly() {
    return ResponseEntity.ok("ok");
  }
}
