package com.dentalclinic.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T058 — {@code POST /patients/{id}/erasure-request} per contracts/patient-api.yaml (FR-010, RODO
 * erasure). {@code 202} for DOCTOR only, {@code 404} otherwise; records a
 * PATIENT_DATA_ERASURE_REQUESTED audit entry (plan.md Constitution Check — the actual
 * anonymization/deletion execution is deferred, tracked at T060).
 */
class PatientErasureApiTest extends PostgresIntegrationTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"Erasure","dateOfBirth":"1990-01-15",
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
  void doctor_canRequestErasure_andItWritesAnAuditEntry() throws Exception {
    UUID id = createPatient("90011503023");
    long before = countEntries("PATIENT_DATA_ERASURE_REQUESTED");

    mockMvc
        .perform(
            post("/patients/" + id + "/erasure-request")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isAccepted());

    assertThat(countEntries("PATIENT_DATA_ERASURE_REQUESTED")).isEqualTo(before + 1);
  }

  @Test
  void administratorReceptionAndAssistant_areDenied404() throws Exception {
    UUID id = createPatient("90011503030");

    for (String role : new String[] {"ADMINISTRATOR", "RECEPTION", "ASSISTANT"}) {
      mockMvc
          .perform(
              post("/patients/" + id + "/erasure-request")
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
            post("/patients/" + UUID.randomUUID() + "/erasure-request")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
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
