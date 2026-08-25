package com.dentalclinic.patient.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T057 — {@code POST /patients/{id}/export} per contracts/patient-api.yaml (FR-009, RODO
 * subject-access request). {@code 200} for DOCTOR only — deliberately not ADMINISTRATOR
 * (research.md #6, rbac-policy.md rule 6) — {@code 404} for ADMINISTRATOR/RECEPTION/ASSISTANT.
 */
class PatientExportApiTest extends PostgresIntegrationTestBase {

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"Export","dateOfBirth":"1990-01-15",
       "pesel":"%s","addressStreet":"Polna","addressBuildingNo":"12A",
       "addressPostalCode":"00-001","addressCity":"Warszawa"}
      """;

  private UUID createPatient(String pesel) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/patients")
                    .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                    .cookie(CSRF_TOKEN_COOKIE)
                    .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                    .contentType(APPLICATION_JSON)
                    .content(CREATE_BODY.formatted(pesel)))
            .andExpect(status().isCreated())
            .andReturn();
    String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    return UUID.fromString(id);
  }

  @Test
  void doctor_canExportFullPatientData() throws Exception {
    UUID id = createPatient("90011503009");

    mockMvc
        .perform(
            post("/patients/" + id + "/export")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patient.lastName").value("Export"))
        .andExpect(jsonPath("$.toothChart.length()").value(32))
        .andExpect(jsonPath("$.visitHistory.length()").value(0));
  }

  @Test
  void administratorReceptionAndAssistant_areDenied404() throws Exception {
    UUID id = createPatient("90011503016");

    for (String role : new String[] {"ADMINISTRATOR", "RECEPTION", "ASSISTANT"}) {
      mockMvc
          .perform(
              post("/patients/" + id + "/export")
                  .with(user(UUID.randomUUID().toString()).roles(role))
                  .cookie(CSRF_TOKEN_COOKIE)
                  .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  void nonexistentPatient_returns404() throws Exception {
    mockMvc
        .perform(
            post("/patients/" + UUID.randomUUID() + "/export")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isNotFound());
  }
}
