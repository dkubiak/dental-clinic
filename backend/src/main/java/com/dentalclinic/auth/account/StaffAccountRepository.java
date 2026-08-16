package com.dentalclinic.auth.account;

import com.dentalclinic.auth.role.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAccountRepository extends JpaRepository<StaffAccount, UUID> {

  Optional<StaffAccount> findByEmail(String email);

  /** Used by the FR-009a guard to check whether a target is the last active administrator. */
  long countByRoleAndStatus(Role role, AccountStatus status);
}
