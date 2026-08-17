package com.dentalclinic.auth.auditlog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogEntryRepository
    extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

  /** The current chain tail — the next entry's {@code previousEntryHash} (AuditLogWriter, T025). */
  @Query("SELECT e FROM AuditLogEntry e WHERE e.id = (SELECT MAX(e2.id) FROM AuditLogEntry e2)")
  Optional<AuditLogEntry> findLatest();
}
