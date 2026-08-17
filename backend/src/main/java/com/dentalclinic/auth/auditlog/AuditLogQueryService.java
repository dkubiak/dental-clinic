package com.dentalclinic.auth.auditlog;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * T061 — read-only audit log review (US2, FR-008a — ADMINISTRATOR only; enforced by
 * AuditLogController's {@code @PreAuthorize}, not here). Filters by date range/event type and
 * paginates per {@code GET /audit-log} (contracts/auth-api.yaml); never exposes a write path — this
 * class has no method that could mutate a row (FR-008).
 */
@Service
public class AuditLogQueryService {

  private final AuditLogEntryRepository repository;

  public AuditLogQueryService(AuditLogEntryRepository repository) {
    this.repository = repository;
  }

  public Page<AuditLogEntry> search(
      Instant from, Instant to, AuditEventType eventType, Pageable pageable) {
    return repository.findAll(buildSpecification(from, to, eventType), pageable);
  }

  private Specification<AuditLogEntry> buildSpecification(
      Instant from, Instant to, AuditEventType eventType) {
    return (root, query, builder) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (from != null) {
        predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
      }
      if (to != null) {
        predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), to));
      }
      if (eventType != null) {
        predicates.add(builder.equal(root.get("eventType"), eventType));
      }
      return builder.and(predicates.toArray(new Predicate[0]));
    };
  }
}
