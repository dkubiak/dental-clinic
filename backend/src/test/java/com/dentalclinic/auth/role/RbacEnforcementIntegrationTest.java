package com.dentalclinic.auth.role;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;

/**
 * T036 — out-of-role direct request returns 404, not 403 (FR-005; research.md #8;
 * contracts/rbac-policy.md rule 2), via the test-only admin-scoped endpoint (see
 * TestOnlyAdminController).
 */
class RbacEnforcementIntegrationTest extends PostgresIntegrationTestBase {

  @Test
  void outOfRoleRequest_returns404NotForbidden() throws Exception {
    mockMvc
        .perform(get("/test-support/admin-only").with(user("reception-user").roles("RECEPTION")))
        .andExpect(status().isNotFound());
  }

  @Test
  void genuinelyNonexistentResource_alsoReturns404_indistinguishableFromDenial() throws Exception {
    mockMvc
        .perform(
            get("/test-support/does-not-exist").with(user("reception-user").roles("RECEPTION")))
        .andExpect(status().isNotFound());
  }

  @Test
  void administratorRole_isAllowedThrough() throws Exception {
    mockMvc
        .perform(get("/test-support/admin-only").with(user("admin-user").roles("ADMINISTRATOR")))
        .andExpect(status().isOk());
  }

  @Test
  void doctorRole_isAlsoDenied404_forAdminOnlyResource() throws Exception {
    mockMvc
        .perform(get("/test-support/admin-only").with(user("doctor-user").roles("DOCTOR")))
        .andExpect(status().isNotFound());
  }
}
