package com.dentalclinic.auth.role;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import org.junit.jupiter.api.Test;

/**
 * T071 — RBAC policy matrix grants ADMINISTRATOR zero patient-data permissions (US3 AC3;
 * contracts/rbac-policy.md rule 3), via the test-only clinical-data-scoped endpoint (see
 * TestOnlyAdminController#clinicalDataOnly). RECEPTION/DOCTOR both have clinical-data access in
 * this feature's foundation (the split between "contact details" vs. "medical records" is a future
 * feature's concern — this feature only establishes the role boundary).
 */
class AdministratorNoClinicalAccessTest extends PostgresIntegrationTestBase {

  @Test
  void administratorRole_isDenied404_forClinicalDataResource() throws Exception {
    mockMvc
        .perform(
            get("/test-support/clinical-data-only").with(user("admin-user").roles("ADMINISTRATOR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void receptionRole_isAllowed_forClinicalDataResource() throws Exception {
    mockMvc
        .perform(
            get("/test-support/clinical-data-only").with(user("reception-user").roles("RECEPTION")))
        .andExpect(status().isOk());
  }

  @Test
  void doctorRole_isAllowed_forClinicalDataResource() throws Exception {
    mockMvc
        .perform(get("/test-support/clinical-data-only").with(user("doctor-user").roles("DOCTOR")))
        .andExpect(status().isOk());
  }
}
