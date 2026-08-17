package com.dentalclinic.auth.api;

import com.dentalclinic.auth.auditlog.AuditLogEntry;
import java.util.List;
import org.springframework.data.domain.Page;

/** Paginated {@code GET /audit-log} response body (T063). */
public record AuditLogPageResponse(
    List<AuditLogEntryDto> entries, int page, int size, long totalElements, int totalPages) {

  static AuditLogPageResponse from(Page<AuditLogEntry> page) {
    return new AuditLogPageResponse(
        page.getContent().stream().map(AuditLogEntryDto::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
