package com.dentalclinic.patient.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T032 — {@code GET /diagnosis-catalog} per contracts/patient-api.yaml (FR-011/FR-013/FR-020).
 * DOCTOR/ASSISTANT 200 with {@code q}/{@code quickAccessOnly} filtering, RECEPTION 404, and no
 * write mapping exists anywhere on the path (FR-011, research.md D5).
 */
class DiagnosisCatalogControllerTest extends PostgresIntegrationTestBase {

  @Test
  void doctorAndAssistant_canSearchCatalog() throws Exception {
    for (String role : new String[] {"DOCTOR", "ASSISTANT"}) {
      mockMvc
          .perform(
              get("/diagnosis-catalog").with(user(UUID.randomUUID().toString()).roles(role)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(40)));
    }
  }

  @Test
  void searchByNameFragment_narrowsResults() throws Exception {
    mockMvc
        .perform(
            get("/diagnosis-catalog")
                .param("q", "próchnica")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.namePl == 'Próchnica zębiny')]").exists());
  }

  @Test
  void searchByCodeFragment_narrowsResults() throws Exception {
    mockMvc
        .perform(
            get("/diagnosis-catalog")
                .param("q", "K02.1d")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value("K02.1d"));
  }

  @Test
  void quickAccessOnly_returnsOnlyFlaggedEntries() throws Exception {
    mockMvc
        .perform(
            get("/diagnosis-catalog")
                .param("quickAccessOnly", "true")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.quickAccess == false)]").doesNotExist());
  }

  @Test
  void reception_isDenied404_onSearchCatalog() throws Exception {
    mockMvc
        .perform(
            get("/diagnosis-catalog").with(user(UUID.randomUUID().toString()).roles("RECEPTION")))
        .andExpect(status().isNotFound());
  }

  @Test
  void noWriteMappingExists_onDiagnosisCatalogPath() throws Exception {
    mockMvc
        .perform(post("/diagnosis-catalog").with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().is4xxClientError());
    mockMvc
        .perform(
            patch("/diagnosis-catalog").with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().is4xxClientError());
    mockMvc
        .perform(
            delete("/diagnosis-catalog").with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().is4xxClientError());
  }
}
