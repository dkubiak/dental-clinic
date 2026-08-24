package com.dentalclinic.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T029 — {@code GET /patients?q=} per contracts/patient-api.yaml (FR-012). A successful search
 * writes one {@code PATIENT_RECORD_VIEWED} audit entry per call (FR-007/SC-003).
 */
class PatientSearchApiTest extends PostgresIntegrationTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  private void createPatient(String firstName, String lastName, String pesel) throws Exception {
    String body =
        """
        {"firstName":"%s","lastName":"%s","dateOfBirth":"1990-01-15",
         "pesel":"%s","addressStreet":"Polna","addressBuildingNo":"12A",
         "addressPostalCode":"00-001","addressCity":"Warszawa"}
        """
            .formatted(firstName, lastName, pesel);
    mockMvc
        .perform(
            post("/patients")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  void matchesByLastNameFragment_caseInsensitive_andWritesOneAuditEntry() throws Exception {
    createPatient("Piotr", "Wiśniewski-Search", "90011500068");

    Long viewedCountBefore = countPatientRecordViewedEntries();

    mockMvc
        .perform(
            get("/patients")
                .param("q", "wiśniewski-search")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].lastName").value("Wiśniewski-Search"));

    assertThat(countPatientRecordViewedEntries()).isEqualTo(viewedCountBefore + 1);
  }

  private Long countPatientRecordViewedEntries() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM audit_log_entry WHERE event_type = 'PATIENT_RECORD_VIEWED'::audit_event_type",
        Long.class);
  }

  @Test
  void matchesByExactPesel() throws Exception {
    createPatient("Ewa", "Kamińska-Search", "90011500075");

    mockMvc
        .perform(
            get("/patients")
                .param("q", "90011500075")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].pesel").value("90011500075"));
  }

  @Test
  void assistant_canSearch() throws Exception {
    mockMvc
        .perform(
            get("/patients")
                .param("q", "NoSuchLastName")
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void administrator_isDenied404() throws Exception {
    mockMvc
        .perform(
            get("/patients")
                .param("q", "anything")
                .with(user(UUID.randomUUID().toString()).roles("ADMINISTRATOR")))
        .andExpect(status().isNotFound());
  }
}
