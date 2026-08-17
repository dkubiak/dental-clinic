package com.dentalclinic.auth.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * T039e — login attempts from a single source IP against multiple different accounts are rejected
 * with 429 once the per-IP threshold is exceeded, independent of any single account's own lockout
 * state (FR-011b). Uses a lowered threshold (via {@code @DynamicPropertySource}, scoped to this
 * test class's own Spring context) so the test doesn't need dozens of requests to trigger it.
 */
class IpRateLimitIntegrationTest extends PostgresIntegrationTestBase {

  private static final String SHARED_SOURCE_IP = "10.0.6.1";

  @Autowired private TestAccountFactory testAccountFactory;

  @DynamicPropertySource
  static void lowerRateLimitThreshold(DynamicPropertyRegistry registry) {
    registry.add("app.ip-rate-limit.max-attempts-per-window", () -> "3");
  }

  @Test
  void exceedingPerIpThresholdAcrossDifferentAccounts_returns429() throws Exception {
    for (int i = 0; i < 3; i++) {
      testAccountFactory.createActiveAccount(
          "ip-rate-limit-test-%d@dentalclinic.example".formatted(i),
          "correct-horse-battery",
          Role.RECEPTION);
    }

    // 3 attempts against 3 different accounts, all from the same IP, stay under the threshold —
    // each fails for its own reason (wrong password), not rate limiting.
    for (int i = 0; i < 3; i++) {
      mockMvc
          .perform(
              post("/auth/login")
                  .header("X-Forwarded-For", SHARED_SOURCE_IP)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"email\":\"ip-rate-limit-test-%d@dentalclinic.example\",\"password\":\"wrong-password12\"}"
                          .formatted(i)))
          .andExpect(status().isUnauthorized());
    }

    // 4th attempt from the same IP, against yet another (nonexistent) account, exceeds the
    // threshold — rejected before credentials are even evaluated (FR-011b, checked first).
    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", SHARED_SOURCE_IP)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"someone-else@dentalclinic.example","password":"wrong-password12"}
                    """))
        .andExpect(status().isTooManyRequests());
  }
}
