package com.dentalclinic.auth.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T037 — deactivated account cannot log in (FR-010). */
class DeactivatedAccountLoginTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private StaffAccountRepository staffAccountRepository;

  @Test
  void deactivatedAccount_correctPassword_returns403() throws Exception {
    String email = "deactivated-test@dentalclinic.example";
    String password = "correct-horse-battery";
    StaffAccount account = testAccountFactory.createActiveAccount(email, password, Role.DOCTOR);
    account.setStatus(AccountStatus.DEACTIVATED);
    staffAccountRepository.save(account);

    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", "10.0.2.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
        .andExpect(status().isForbidden());
  }
}
