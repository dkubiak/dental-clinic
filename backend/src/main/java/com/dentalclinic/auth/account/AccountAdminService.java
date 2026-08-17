package com.dentalclinic.auth.account;

import com.dentalclinic.auth.auditlog.AuditEventType;
import com.dentalclinic.auth.auditlog.AuditLogWriter;
import com.dentalclinic.auth.mfa.MfaEnrollmentRepository;
import com.dentalclinic.auth.passwordreset.PasswordResetService;
import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.session.SessionInvalidationService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T074 — create/deactivate/reactivate/change-role for staff accounts (FR-009, US3), admin-only
 * (enforced by AccountController's {@code @PreAuthorize}, not here).
 */
@Service
public class AccountAdminService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final StaffAccountRepository staffAccountRepository;
  private final MfaEnrollmentRepository mfaEnrollmentRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordResetService passwordResetService;
  private final SessionInvalidationService sessionInvalidationService;
  private final AuditLogWriter auditLogWriter;

  public AccountAdminService(
      StaffAccountRepository staffAccountRepository,
      MfaEnrollmentRepository mfaEnrollmentRepository,
      PasswordEncoder passwordEncoder,
      PasswordResetService passwordResetService,
      SessionInvalidationService sessionInvalidationService,
      AuditLogWriter auditLogWriter) {
    this.staffAccountRepository = staffAccountRepository;
    this.mfaEnrollmentRepository = mfaEnrollmentRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordResetService = passwordResetService;
    this.sessionInvalidationService = sessionInvalidationService;
    this.auditLogWriter = auditLogWriter;
  }

  public List<StaffAccount> listAll() {
    return staffAccountRepository.findAll();
  }

  /**
   * {@code contracts/auth-api.yaml}'s {@code POST /accounts} takes only email+role — no initial
   * password field — so the account is created with an unguessable, never-revealed placeholder
   * password hash, and this immediately triggers the existing self-service reset email (T043,
   * FR-016) so the new staff member sets their own first password. That password is validated by
   * PasswordPolicyValidator inside {@code PasswordResetService.confirmReset} exactly as any other
   * reset is (T043a) — this deliberately reuses that flow instead of adding a second, parallel "set
   * initial password" mechanism.
   */
  @Transactional
  public StaffAccount create(String email, Role role, UUID actorId) {
    StaffAccount account =
        new StaffAccount(
            UUID.randomUUID(),
            email,
            passwordEncoder.encode(generateUnguessablePlaceholder()),
            role,
            actorId);
    staffAccountRepository.save(account);
    passwordResetService.requestReset(email);
    auditLogWriter.append(
        AuditEventType.ACCOUNT_CREATED, actorId, account.getId(), null, roleJson(role), null);
    return account;
  }

  /**
   * {@code noRollbackFor}: the ACCOUNT_DEACTIVATION_DENIED_LAST_ADMIN audit entry (FR-009a) is
   * written BEFORE {@link LastAdministratorException} is thrown — without this, Spring's default
   * rollback-on-RuntimeException would discard that very audit entry (see AuthService's identical
   * javadoc note on this pattern).
   *
   * @throws AccountNotFoundException the target id does not exist.
   * @throws LastAdministratorException FR-009a — target is the last active administrator. See
   *     StaffAccountRepository#findActiveAdministratorsForUpdate for how FR-009b's atomicity
   *     guarantee (no window where two concurrent requests both pass) is achieved.
   */
  @Transactional(noRollbackFor = LastAdministratorException.class)
  public void deactivate(UUID targetId, UUID actorId) {
    StaffAccount target =
        staffAccountRepository.findById(targetId).orElseThrow(AccountNotFoundException::new);

    if (target.getRole() == Role.ADMINISTRATOR) {
      List<StaffAccount> activeAdministrators =
          staffAccountRepository.findActiveAdministratorsForUpdate(
              Role.ADMINISTRATOR, AccountStatus.ACTIVE);
      boolean isLastActiveAdministrator =
          activeAdministrators.size() == 1 && activeAdministrators.get(0).getId().equals(targetId);
      if (isLastActiveAdministrator) {
        auditLogWriter.append(
            AuditEventType.ACCOUNT_DEACTIVATION_DENIED_LAST_ADMIN,
            actorId,
            targetId,
            null,
            null,
            null);
        throw new LastAdministratorException();
      }
    }

    target.setStatus(AccountStatus.DEACTIVATED);
    staffAccountRepository.save(target);
    sessionInvalidationService.invalidateSessionsFor(targetId);
    auditLogWriter.append(AuditEventType.ACCOUNT_DEACTIVATED, actorId, targetId, null, null, null);
  }

  /**
   * @throws AccountNotFoundException the target id does not exist.
   */
  @Transactional
  public void reactivate(UUID targetId, UUID actorId) {
    StaffAccount target =
        staffAccountRepository.findById(targetId).orElseThrow(AccountNotFoundException::new);
    target.setStatus(AccountStatus.ACTIVE);
    staffAccountRepository.save(target);
    auditLogWriter.append(AuditEventType.ACCOUNT_REACTIVATED, actorId, targetId, null, null, null);
  }

  /**
   * Immediately invalidates the target's active sessions (FR-007a, T075a) so the new role takes
   * effect on the next request, not merely the next login.
   *
   * @throws AccountNotFoundException the target id does not exist.
   */
  @Transactional
  public StaffAccount changeRole(UUID targetId, Role newRole, UUID actorId) {
    StaffAccount target =
        staffAccountRepository.findById(targetId).orElseThrow(AccountNotFoundException::new);
    Role oldRole = target.getRole();
    target.setRole(newRole);
    staffAccountRepository.save(target);
    sessionInvalidationService.invalidateSessionsFor(targetId);
    auditLogWriter.append(
        AuditEventType.ROLE_CHANGED, actorId, targetId, roleJson(oldRole), roleJson(newRole), null);
    return target;
  }

  /**
   * Admin-assisted MFA reset for a lost device (FR-015b) — deletes the target's MfaEnrollment row,
   * forcing re-enrollment on its next login (MfaService#ensureEnrollment treats an absent row as
   * never-enrolled).
   *
   * @throws AccountNotFoundException the target id does not exist.
   */
  @Transactional
  public void resetMfa(UUID targetId, UUID actorId) {
    StaffAccount target =
        staffAccountRepository.findById(targetId).orElseThrow(AccountNotFoundException::new);
    // existsById guard: a target that was created but never completed first-login enrollment has
    // no MfaEnrollment row yet — deleteById would otherwise throw EmptyResultDataAccessException.
    if (mfaEnrollmentRepository.existsById(targetId)) {
      mfaEnrollmentRepository.deleteById(targetId);
    }
    // AuthService#login only surfaces enrollment-setup info (secret/QR) when mfaEnrolled is
    // false — without also flipping this back, the next login would find no MfaEnrollment row
    // (MfaService#verifyCode returns false for everything) yet never show the new QR code either.
    target.setMfaEnrolled(false);
    staffAccountRepository.save(target);
    auditLogWriter.append(AuditEventType.MFA_RESET, actorId, targetId, null, null, null);
  }

  private static String roleJson(Role role) {
    return "{\"role\":\"" + role.name() + "\"}";
  }

  private static String generateUnguessablePlaceholder() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
