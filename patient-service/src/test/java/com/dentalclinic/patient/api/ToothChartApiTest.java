package com.dentalclinic.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * T042 — {@code GET/PATCH /patients/{id}/tooth-chart[/{toothNumber}]} per
 * contracts/patient-api.yaml (FR-005/FR-006). {@code 200} for DOCTOR/ASSISTANT, {@code 404} for
 * RECEPTION; GET writes a TOOTH_CHART_VIEWED audit entry, PATCH writes TOOTH_STATE_CHANGED with
 * before/after.
 */
class ToothChartApiTest extends PostgresIntegrationTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"ToothChart","dateOfBirth":"1990-01-15",
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
          .andExpect(jsonPath("$.length()").value(32))
          .andExpect(jsonPath("$[0].status").value("HEALTHY"));
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
  void nonexistentPatient_returns404_onReadChart() throws Exception {
    mockMvc
        .perform(
            get("/patients/" + UUID.randomUUID() + "/tooth-chart")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void doctor_canToggleToothStatus_andItWritesAnAuditEntryWithBeforeAfter() throws Exception {
    UUID id = createPatient("90011502022");
    long before = countEntries("TOOTH_STATE_CHANGED");

    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/11")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"SICK\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.toothNumber").value(11))
        .andExpect(jsonPath("$.status").value("SICK"));

    assertThat(countEntries("TOOTH_STATE_CHANGED")).isEqualTo(before + 1);
    String afterState =
        jdbcTemplate.queryForObject(
            "SELECT after_state::text FROM audit_log_entry WHERE event_type ="
                + " 'TOOTH_STATE_CHANGED'::audit_event_type ORDER BY id DESC LIMIT 1",
            String.class);
    assertThat(afterState).contains("SICK");
  }

  @Test
  void assistant_canToggleToothStatus() throws Exception {
    UUID id = createPatient("90011502039");

    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/48")
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"SICK\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SICK"));
  }

  @Test
  void reception_isDenied404_onToggleToothStatus() throws Exception {
    UUID id = createPatient("90011502046");

    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/11")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"SICK\"}"))
        .andExpect(status().isNotFound());
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
