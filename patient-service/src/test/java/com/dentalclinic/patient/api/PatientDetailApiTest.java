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
import com.dentalclinic.patient.record.PatientRecord;
import com.dentalclinic.patient.record.PatientRecordRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T030 — {@code GET /patients/{id}} (readable by RECEPTION/DOCTOR/ASSISTANT, writes
 * PATIENT_RECORD_VIEWED) and {@code PATCH /patients/{id}} (RECEPTION/DOCTOR only) per
 * contracts/patient-api.yaml (FR-001/FR-011).
 */
class PatientDetailApiTest extends PostgresIntegrationTestBase {

  @Autowired private PatientRecordRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"Detail","dateOfBirth":"1990-01-15",
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
  void reception_doctor_assistant_canReadDetail_andItWritesAnAuditEntry() throws Exception {
    UUID id = createPatient("90011500082");
    long before = countPatientRecordViewedEntries();

    for (String role : new String[] {"RECEPTION", "DOCTOR", "ASSISTANT"}) {
      mockMvc
          .perform(get("/patients/" + id).with(user(UUID.randomUUID().toString()).roles(role)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.lastName").value("Detail"));
    }

    assertThat(countPatientRecordViewedEntries()).isEqualTo(before + 3);
  }

  @Test
  void administrator_isDenied404_onRead() throws Exception {
    UUID id = createPatient("90011500099");

    mockMvc
        .perform(
            get("/patients/" + id).with(user(UUID.randomUUID().toString()).roles("ADMINISTRATOR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void nonexistentPatient_returns404() throws Exception {
    mockMvc
        .perform(
            get("/patients/" + UUID.randomUUID())
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isNotFound());
  }

  @Test
  void reception_canEditBasicData() throws Exception {
    UUID id = createPatient("90011500105");
    String updateBody =
        """
        {"firstName":"Janusz","lastName":"Detail","dateOfBirth":"1990-01-15",
         "addressStreet":"Nowa","addressBuildingNo":"1",
         "addressPostalCode":"00-999","addressCity":"Gdynia"}
        """;

    mockMvc
        .perform(
            patch("/patients/" + id)
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Janusz"))
        .andExpect(jsonPath("$.addressCity").value("Gdynia"));

    PatientRecord updated = repository.findById(id).orElseThrow();
    assertThat(updated.getFirstName()).isEqualTo("Janusz");
    assertThat(updated.getPesel()).isNull();
  }

  @Test
  void assistant_isDenied404_onEdit() throws Exception {
    UUID id = createPatient("90011500112");
    String updateBody = CREATE_BODY.formatted("90011500129");

    mockMvc
        .perform(
            patch("/patients/" + id)
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isNotFound());
  }

  @Test
  void editWithBadPeselChecksum_returns400() throws Exception {
    UUID id = createPatient("90011500136");
    String updateBody =
        CREATE_BODY.formatted("90011500144"); // last digit altered from a valid PESEL

    mockMvc
        .perform(
            patch("/patients/" + id)
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isBadRequest());
  }

  @Test
  void editToAnotherRecordsPesel_returns409() throws Exception {
    createPatient("90011500150");
    UUID second = createPatient("90011500167");
    String updateBody = CREATE_BODY.formatted("90011500150");

    mockMvc
        .perform(
            patch("/patients/" + second)
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isConflict());
  }

  private long countPatientRecordViewedEntries() {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log_entry WHERE event_type ="
                + " 'PATIENT_RECORD_VIEWED'::audit_event_type",
            Long.class);
    return count == null ? 0 : count;
  }
}
