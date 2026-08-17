package com.dentalclinic.auth.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.account.StaffAccount;
import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T056 — an admin role change ({@code PATCH /accounts/{id}}) produces a {@code ROLE_CHANGED} audit
 * entry with the correct {@code before_state}/{@code after_state} and actor (FR-007).
 */
class RoleChangeAuditIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private AuditLogEntryRepository auditLogEntryRepository;

  @Test
  void roleChange_producesRoleChangedEntry_withBeforeAndAfterState() throws Exception {
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "role-change-audit@dentalclinic.example", "irrelevant-password-1", Role.DOCTOR);
    // actor_account_id is a real FK against staff_account (V5__audit_log.sql) — the acting admin
    // must be a persisted account, not just a synthetic MockMvc principal.
    StaffAccount adminActor =
        testAccountFactory.createActiveAccount(
            "role-change-admin-actor@dentalclinic.example",
            "irrelevant-password-1",
            Role.ADMINISTRATOR);
    UUID adminActorId = adminActor.getId();

    mockMvc
        .perform(
            patch("/accounts/" + target.getId())
                .with(user(adminActorId.toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"role\":\"ADMINISTRATOR\"}"))
        .andExpect(status().isOk());

    List<AuditLogEntry> entries = auditLogEntryRepository.findAll();
    AuditLogEntry roleChanged =
        entries.stream()
            .filter(
                e ->
                    e.getEventType() == AuditEventType.ROLE_CHANGED
                        && target.getId().equals(e.getTargetAccountId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Expected a ROLE_CHANGED entry for target"));

    assertThat(roleChanged.getActorAccountId()).isEqualTo(adminActorId);
    assertThat(roleChanged.getBeforeState()).contains("DOCTOR");
    assertThat(roleChanged.getAfterState()).contains("ADMINISTRATOR");
  }
}
