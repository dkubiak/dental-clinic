package com.dentalclinic.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.account.AccountStatus;
import com.dentalclinic.auth.account.StaffAccount;
import com.dentalclinic.auth.account.StaffAccountRepository;
import com.dentalclinic.auth.auditlog.AuditEventType;
import com.dentalclinic.auth.auditlog.AuditLogEntryRepository;
import com.dentalclinic.auth.mfa.MfaEnrollmentRepository;
import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T066/T067/T068/T068a/T068b — contract tests for the {@code /accounts} endpoints per
 * contracts/auth-api.yaml: {@code POST /accounts} (create), {@code PATCH /accounts/{id}} (role
 * change), {@code POST .../deactivate}/{@code .../reactivate}, the FR-009a last-administrator
 * guard, and {@code POST .../mfa-reset} (FR-015b). Uses the full Testcontainers-backed context —
 * see AuditLogControllerContractTest's javadoc for why an RBAC-gated endpoint's contract cannot be
 * proven by a {@code @WebMvcTest} slice alone.
 */
class AccountControllerContractTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private StaffAccountRepository staffAccountRepository;
  @Autowired private MfaEnrollmentRepository mfaEnrollmentRepository;
  @Autowired private AuditLogEntryRepository auditLogEntryRepository;

  private StaffAccount adminActor() {
    return testAccountFactory.createActiveAccount(
        "account-contract-admin-" + UUID.randomUUID() + "@dentalclinic.example",
        "irrelevant-password-1",
        Role.ADMINISTRATOR);
  }

  @Test
  void administrator_canCreateAccount_andItTriggersASetPasswordEmail() throws Exception {
    StaffAccount admin = adminActor();
    String newEmail = "new-staff-" + UUID.randomUUID() + "@dentalclinic.example";

    mockMvc
        .perform(
            post("/accounts")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"role\":\"RECEPTION\"}".formatted(newEmail)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(newEmail))
        .andExpect(jsonPath("$.role").value("RECEPTION"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    verify(emailSender).send(eq(newEmail), anyString(), anyString());

    boolean created =
        auditLogEntryRepository.findAll().stream()
            .anyMatch(
                e ->
                    e.getEventType() == AuditEventType.ACCOUNT_CREATED
                        && admin.getId().equals(e.getActorAccountId()));
    assertThat(created).isTrue();
  }

  @Test
  void nonAdministrator_isDenied404_onCreate() throws Exception {
    mockMvc
        .perform(
            post("/accounts")
                .with(user("reception-caller").roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"whoever@dentalclinic.example\",\"role\":\"RECEPTION\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void administrator_canChangeRole() throws Exception {
    StaffAccount admin = adminActor();
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "role-change-contract@dentalclinic.example", "irrelevant-password-1", Role.RECEPTION);

    mockMvc
        .perform(
            patch("/accounts/" + target.getId())
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"role\":\"DOCTOR\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("DOCTOR"));
  }

  @Test
  void changeRole_forNonexistentAccount_returns404() throws Exception {
    StaffAccount admin = adminActor();

    mockMvc
        .perform(
            patch("/accounts/" + UUID.randomUUID())
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"role\":\"DOCTOR\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void administrator_canDeactivateAndReactivateAnAccount() throws Exception {
    StaffAccount admin = adminActor();
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "deactivate-reactivate-contract@dentalclinic.example",
            "irrelevant-password-1",
            Role.RECEPTION);

    mockMvc
        .perform(
            post("/accounts/" + target.getId() + "/deactivate")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isOk());
    assertThat(staffAccountRepository.findById(target.getId()).orElseThrow().getStatus())
        .isEqualTo(AccountStatus.DEACTIVATED);

    mockMvc
        .perform(
            post("/accounts/" + target.getId() + "/reactivate")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isOk());
    assertThat(staffAccountRepository.findById(target.getId()).orElseThrow().getStatus())
        .isEqualTo(AccountStatus.ACTIVE);
  }

  @Test
  void deactivate_forNonexistentAccount_returns404() throws Exception {
    StaffAccount admin = adminActor();

    mockMvc
        .perform(
            post("/accounts/" + UUID.randomUUID() + "/deactivate")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isNotFound());
  }

  @Test
  void deactivatingTheLastActiveAdministrator_returns409_andIsAuditLogged() throws Exception {
    // Isolate this scenario from any other ADMINISTRATOR accounts other test classes created in
    // this shared Testcontainers database (PostgresIntegrationTestBase's singleton-container
    // pattern) — only the account created below may remain ACTIVE for this assertion to hold.
    staffAccountRepository.findAll().stream()
        .filter(a -> a.getRole() == Role.ADMINISTRATOR && a.getStatus() == AccountStatus.ACTIVE)
        .forEach(
            a -> {
              a.setStatus(AccountStatus.DEACTIVATED);
              staffAccountRepository.save(a);
            });

    StaffAccount lastAdmin = adminActor();

    mockMvc
        .perform(
            post("/accounts/" + lastAdmin.getId() + "/deactivate")
                .with(user(lastAdmin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isConflict());

    assertThat(staffAccountRepository.findById(lastAdmin.getId()).orElseThrow().getStatus())
        .isEqualTo(AccountStatus.ACTIVE);

    boolean deniedLogged =
        auditLogEntryRepository.findAll().stream()
            .anyMatch(
                e ->
                    e.getEventType() == AuditEventType.ACCOUNT_DEACTIVATION_DENIED_LAST_ADMIN
                        && lastAdmin.getId().equals(e.getTargetAccountId()));
    assertThat(deniedLogged).isTrue();
  }

  @Test
  void nonAdministrator_isDenied404_onMfaReset() throws Exception {
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "mfa-reset-denied@dentalclinic.example", "irrelevant-password-1", Role.RECEPTION);

    mockMvc
        .perform(
            post("/accounts/" + target.getId() + "/mfa-reset")
                .with(user("reception-caller").roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isNotFound());
  }

  @Test
  void administrator_canResetMfa_clearingEnrollment_andAuditLogging() throws Exception {
    StaffAccount admin = adminActor();
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "mfa-reset-contract@dentalclinic.example", "irrelevant-password-1", Role.DOCTOR);
    testAccountFactory.enrollMfa(target);
    assertThat(mfaEnrollmentRepository.existsById(target.getId())).isTrue();

    mockMvc
        .perform(
            post("/accounts/" + target.getId() + "/mfa-reset")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isOk());

    assertThat(mfaEnrollmentRepository.existsById(target.getId())).isFalse();
    assertThat(staffAccountRepository.findById(target.getId()).orElseThrow().isMfaEnrolled())
        .isFalse();

    boolean resetLogged =
        auditLogEntryRepository.findAll().stream()
            .anyMatch(
                e ->
                    e.getEventType() == AuditEventType.MFA_RESET
                        && admin.getId().equals(e.getActorAccountId())
                        && target.getId().equals(e.getTargetAccountId()));
    assertThat(resetLogged).isTrue();
  }

  @Test
  void administrator_canResetMfa_forAccountNeverEnrolled() throws Exception {
    StaffAccount admin = adminActor();
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "mfa-reset-never-enrolled@dentalclinic.example",
            "irrelevant-password-1",
            Role.RECEPTION);

    mockMvc
        .perform(
            post("/accounts/" + target.getId() + "/mfa-reset")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isOk());
  }

  @Test
  void administrator_canListAccounts() throws Exception {
    StaffAccount admin = adminActor();

    mockMvc
        .perform(
            get("/accounts")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
