package com.dentalclinic.auth.api;

import com.dentalclinic.auth.auditlog.AuditEventType;
import com.dentalclinic.auth.auditlog.AuditLogQueryService;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T063 — {@code GET /audit-log} per contracts/auth-api.yaml. Read-only by construction: no other
 * mapping exists on this controller and never will (FR-008 — no PATCH/PUT/DELETE path anywhere in
 * this API for audit log entries). {@code @PreAuthorize} + GlobalExceptionHandler's 404-not-403
 * mapping (T028/T047) enforce FR-008a — a non-administrator caller gets an indistinguishable-from-
 * nonexistent 404, never a 403 revealing the endpoint exists.
 */
@RestController
public class AuditLogController {

  private final AuditLogQueryService auditLogQueryService;

  public AuditLogController(AuditLogQueryService auditLogQueryService) {
    this.auditLogQueryService = auditLogQueryService;
  }

  @GetMapping("/audit-log")
  @PreAuthorize("hasRole('ADMINISTRATOR')")
  public ResponseEntity<AuditLogPageResponse> list(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) AuditEventType eventType,
      Pageable pageable) {
    var page = auditLogQueryService.search(from, to, eventType, pageable);
    return ResponseEntity.ok(AuditLogPageResponse.from(page));
  }
}
