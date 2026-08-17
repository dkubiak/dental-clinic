package com.dentalclinic.auth.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.account.StaffAccount;
import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T055 — a real login success and a real login failure produce the corresponding audit entries, and
 * no entry anywhere ever contains the plaintext password (FR-006).
 */
class AuditLogContentIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private AuditLogEntryRepository auditLogEntryRepository;
  @Autowired private TestAccountFactory testAccountFactory;

  private static final String RAW_PASSWORD = "audit-content-password-1";

  @Test
  void loginSuccessAndFailure_produceCorrectEntries_withNoPlaintextPasswordAnywhere()
      throws Exception {
    StaffAccount account =
        testAccountFactory.createActiveAccount(
            "audit-content@dentalclinic.example", RAW_PASSWORD, Role.RECEPTION);
    String secret = testAccountFactory.enrollMfa(account);

    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", "10.20.30.41")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"wrong-wrong-wrong-1\"}"
                        .formatted(account.getEmail())))
        .andExpect(status().isUnauthorized());

    Cookie sessionCookie =
        loginAndGetSessionCookie(
            account.getEmail(),
            RAW_PASSWORD,
            "10.20.30.42",
            () -> testAccountFactory.currentTotpCode(secret));
    assertThat(sessionCookie).isNotNull();

    List<AuditLogEntry> entries = auditLogEntryRepository.findAll();

    assertThat(entries)
        .anyMatch(
            e ->
                e.getEventType() == AuditEventType.LOGIN_FAILURE
                    && account.getId().equals(e.getActorAccountId()));
    assertThat(entries)
        .anyMatch(
            e ->
                e.getEventType() == AuditEventType.LOGIN_SUCCESS
                    && account.getId().equals(e.getActorAccountId()));

    for (AuditLogEntry entry : entries) {
      assertThat(safe(entry.getMetadata())).doesNotContain(RAW_PASSWORD);
      assertThat(safe(entry.getBeforeState())).doesNotContain(RAW_PASSWORD);
      assertThat(safe(entry.getAfterState())).doesNotContain(RAW_PASSWORD);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
