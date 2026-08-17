package com.dentalclinic.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T068c — two concurrent {@code POST /accounts/{id}/deactivate} requests targeting the two
 * remaining active ADMINISTRATOR accounts must result in exactly one succeeding (200) and one
 * refused (409) — no window where both pass validation (FR-009b). See
 * StaffAccountRepository#findActiveAdministratorsForUpdate for the {@code SELECT ... FOR UPDATE}
 * mechanism this proves.
 */
class ConcurrentAdminDeactivationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private StaffAccountRepository staffAccountRepository;

  @Test
  void concurrentDeactivationOfTheLastTwoAdmins_exactlyOneSucceeds() throws Exception {
    // Isolate: only the two accounts created below may remain ACTIVE administrators (shared
    // Testcontainers database across test classes — PostgresIntegrationTestBase).
    staffAccountRepository.findAll().stream()
        .filter(a -> a.getRole() == Role.ADMINISTRATOR && a.getStatus() == AccountStatus.ACTIVE)
        .forEach(
            a -> {
              a.setStatus(AccountStatus.DEACTIVATED);
              staffAccountRepository.save(a);
            });

    StaffAccount adminA =
        testAccountFactory.createActiveAccount(
            "concurrent-admin-a@dentalclinic.example", "irrelevant-password-1", Role.ADMINISTRATOR);
    StaffAccount adminB =
        testAccountFactory.createActiveAccount(
            "concurrent-admin-b@dentalclinic.example", "irrelevant-password-1", Role.ADMINISTRATOR);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch readyLatch = new CountDownLatch(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    try {
      var futureA =
          executor.submit(() -> deactivate(adminA.getId(), adminA.getId(), readyLatch, startLatch));
      var futureB =
          executor.submit(() -> deactivate(adminB.getId(), adminB.getId(), readyLatch, startLatch));

      readyLatch.await(5, TimeUnit.SECONDS);
      startLatch.countDown();

      int statusA = futureA.get(10, TimeUnit.SECONDS);
      int statusB = futureB.get(10, TimeUnit.SECONDS);

      assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(200, 409);
    } finally {
      executor.shutdownNow();
    }

    // Isolated to exactly adminA/adminB above — exactly one deactivation succeeded, so exactly
    // one of the two must remain active (never zero, never both).
    long activeAdminsRemaining =
        staffAccountRepository.countByRoleAndStatus(Role.ADMINISTRATOR, AccountStatus.ACTIVE);
    assertThat(activeAdminsRemaining).isEqualTo(1);
  }

  private int deactivate(
      java.util.UUID targetId, java.util.UUID actorId, CountDownLatch ready, CountDownLatch start) {
    try {
      ready.countDown();
      start.await(5, TimeUnit.SECONDS);
      return mockMvc
          .perform(
              post("/accounts/" + targetId + "/deactivate")
                  .with(user(actorId.toString()).roles("ADMINISTRATOR"))
                  .cookie(CSRF_TOKEN_COOKIE)
                  .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
