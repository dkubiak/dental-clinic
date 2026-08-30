package com.dentalclinic.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T057 — {@code POST /patients/{id}/export} per contracts/patient-api.yaml (FR-009, RODO
 * subject-access request). {@code 200} for DOCTOR only — deliberately not ADMINISTRATOR
 * (research.md #6, rbac-policy.md rule 6) — {@code 404} for ADMINISTRATOR/RECEPTION/ASSISTANT.
 */
class PatientExportApiTest extends PostgresIntegrationTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

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
        .andExpect(jsonPath("$.toothChart.dentitionMode").value("PERMANENT"))
        .andExpect(jsonPath("$.toothChart.positions.length()").value(52))
        .andExpect(jsonPath("$.visitHistory.length()").value(0));
  }

  /**
   * T123 — the export's toothChart carries the FULL finding history per position (current,
   * resolved, and superseded alike), not just the current view (FR-061).
   */
  @Test
  void toothChartInExport_includesResolvedAndSupersededFindings_notJustCurrent() throws Exception {
    UUID id = createPatient("90011503337");
    String cariesEntryId =
        jdbcTemplate.queryForObject(
            "SELECT id::text FROM diagnosis_catalog_entry WHERE code = 'K02.1'", String.class);

    MvcResult createResult =
        mockMvc
            .perform(
                post("/patients/" + id + "/tooth-chart/findings")
                    .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                    .cookie(CSRF_TOKEN_COOKIE)
                    .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"fdiNumber":26,"diagnosisCatalogEntryId":"%s","surfaces":["MESIAL"],
                         "diagnosisDate":"2026-01-01"}
                        """
                            .formatted(cariesEntryId)))
            .andExpect(status().isCreated())
            .andReturn();
    String originalId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings/" + originalId + "/close")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"resolvedDate\":\"2026-08-30\"}"))
        .andExpect(status().isCreated());

    MvcResult exportResult =
        mockMvc
            .perform(
                post("/patients/" + id + "/export")
                    .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                    .cookie(CSRF_TOKEN_COOKIE)
                    .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    String body = exportResult.getResponse().getContentAsString();
    List<String> recordStatuses =
        JsonPath.read(
            body, "$.toothChart.positions[?(@.fdiNumber == 26)].currentFindings[*].recordStatus");

    assertThat(recordStatuses).containsExactlyInAnyOrder("CURRENT", "SUPERSEDED");
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
