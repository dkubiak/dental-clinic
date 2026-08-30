package com.dentalclinic.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T031 — {@code GET /patients/{id}/tooth-chart[/positions/{fdi}/history]} per
 * contracts/patient-api.yaml (FR-005/FR-034/FR-059). 200 for DOCTOR/ASSISTANT, 404 for RECEPTION
 * and for a nonexistent patient (indistinguishable, FR-059); one TOOTH_CHART_VIEWED audit row per
 * successful read.
 */
class ToothChartControllerTest extends PostgresIntegrationTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"Odontogram","dateOfBirth":"1990-01-15",
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
  void doctorAndAssistant_canReadChart_andItWritesAnAuditEntry() throws Exception {
    UUID id = createPatient("90011502008");
    long before = countEntries("TOOTH_CHART_VIEWED");

    for (String role : new String[] {"DOCTOR", "ASSISTANT"}) {
      mockMvc
          .perform(
              get("/patients/" + id + "/tooth-chart")
                  .with(user(UUID.randomUUID().toString()).roles(role)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dentitionMode").value("PERMANENT"))
          .andExpect(jsonPath("$.positions.length()").value(52))
          .andExpect(jsonPath("$.positions[0].presence").value("PRESENT"));
    }

    assertThat(countEntries("TOOTH_CHART_VIEWED")).isEqualTo(before + 2);
  }

  @Test
  void reception_isDenied404_onReadChart() throws Exception {
    UUID id = createPatient("90011502015");

    mockMvc
        .perform(
            get("/patients/" + id + "/tooth-chart")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION")))
        .andExpect(status().isNotFound());
  }

  @Test
  void administrator_isDenied404_onReadChart() throws Exception {
    UUID id = createPatient("90011502022");

    mockMvc
        .perform(
            get("/patients/" + id + "/tooth-chart")
                .with(user(UUID.randomUUID().toString()).roles("ADMINISTRATOR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonexistentPatient_returns404_onReadChart_indistinguishableFromDenial() throws Exception {
    mockMvc
        .perform(
            get("/patients/" + UUID.randomUUID() + "/tooth-chart")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void positionHistory_returnsEmptyForFreshTooth_andIsAuditedAsChartViewed() throws Exception {
    UUID id = createPatient("90011502039");
    long before = countEntries("TOOTH_CHART_VIEWED");

    mockMvc
        .perform(
            get("/patients/" + id + "/tooth-chart/positions/11/history")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    assertThat(countEntries("TOOTH_CHART_VIEWED")).isEqualTo(before + 1);
  }

  private long countEntries(String eventType) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log_entry WHERE event_type = '"
                + eventType
                + "'::audit_event_type",
            Long.class);
    return count == null ? 0 : count;
  }
}
