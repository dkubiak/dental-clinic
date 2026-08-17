package com.dentalclinic.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.account.StaffAccount;
import com.dentalclinic.auth.auditlog.AuditEventType;
import com.dentalclinic.auth.auditlog.AuditLogWriter;
import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T054 — contract test for {@code GET /audit-log} per contracts/auth-api.yaml: admin-only access
 * (404 for a non-admin caller, per FR-005/FR-008a) and the filterable/paginated response shape.
 *
 * <p>Uses the full Testcontainers-backed application context (unlike LoginControllerContractTest's
 * mocked {@code @WebMvcTest} slice) because a {@code @WebMvcTest} slice does not include
 * SecurityConfig's {@code @EnableMethodSecurity} — it could not actually exercise the
 * {@code @PreAuthorize} rule this test exists to verify, since this endpoint's contract IS its RBAC
 * behavior, not just its DTO shape.
 */
class AuditLogControllerContractTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private AuditLogWriter auditLogWriter;

  @Test
  void administrator_canListAuditLog() throws Exception {
    StaffAccount account =
        testAccountFactory.createActiveAccount(
            "audit-contract-list@dentalclinic.example", "irrelevant-password-1", Role.RECEPTION);
    auditLogWriter.append(AuditEventType.LOGIN_SUCCESS, account.getId(), null, null, null, null);

    mockMvc
        .perform(get("/audit-log").with(user("admin-caller").roles("ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries").isArray())
        .andExpect(jsonPath("$.totalElements").isNumber())
        .andExpect(jsonPath("$.page").value(0));
  }

  @Test
  void administrator_canFilterByEventType() throws Exception {
    StaffAccount account =
        testAccountFactory.createActiveAccount(
            "audit-contract-filter@dentalclinic.example", "irrelevant-password-1", Role.DOCTOR);
    // MFA_RESET is only ever written by this feature's Phase 5 flows — filtering on it avoids
    // collision with the high-volume LOGIN_SUCCESS/LOGIN_FAILURE/MFA_FAILURE entries every other
    // test class in this shared-container run also produces (PostgresIntegrationTestBase).
    auditLogWriter.append(
        AuditEventType.MFA_RESET, account.getId(), account.getId(), null, null, null);

    MvcResult result =
        mockMvc
            .perform(
                get("/audit-log")
                    .param("eventType", "MFA_RESET")
                    .with(user("admin-caller").roles("ADMINISTRATOR")))
            .andExpect(status().isOk())
            .andReturn();

    List<String> eventTypes =
        JsonPath.read(result.getResponse().getContentAsString(), "$.entries[*].eventType");
    assertThat(eventTypes).isNotEmpty().allMatch("MFA_RESET"::equals);
  }

  @Test
  void receptionRole_isDenied404_notForbidden() throws Exception {
    mockMvc
        .perform(get("/audit-log").with(user("reception-caller").roles("RECEPTION")))
        .andExpect(status().isNotFound());
  }

  @Test
  void doctorRole_isDenied404_notForbidden() throws Exception {
    mockMvc
        .perform(get("/audit-log").with(user("doctor-caller").roles("DOCTOR")))
        .andExpect(status().isNotFound());
  }
}
