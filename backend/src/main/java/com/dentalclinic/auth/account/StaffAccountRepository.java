package com.dentalclinic.auth.account;

import com.dentalclinic.auth.role.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, UUID> {

  Optional<StaffAccount> findByEmail(String email);

  /** Used by the FR-009a guard to check whether a target is the last active administrator. */
  long countByRoleAndStatus(Role role, AccountStatus status);

  /**
   * {@code SELECT ... FOR UPDATE} over every currently-active administrator row (T074a/FR-009b):
   * two concurrent deactivation requests both call this before deciding whether their own target is
   * "the last one" — since both lock the SAME row set (every active administrator, not just their
   * own target), the second transaction blocks until the first commits, then re-reads a set that
   * has already shrunk, making the check-then-act atomic without a separate advisory lock or
   * SERIALIZABLE isolation level.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM StaffAccount a WHERE a.role = :role AND a.status = :status")
  List<StaffAccount> findActiveAdministratorsForUpdate(
      @Param("role") Role role, @Param("status") AccountStatus status);
}
