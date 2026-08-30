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
 * T051 — {@code POST /patients/{id}/tooth-chart/findings} per contracts/patient-api.yaml
 * (FR-022/FR-030/FR-057). 201 for DOCTOR/ASSISTANT with recordStatus:CURRENT/clinicalStatus:ACTIVE/
 * authorRole set correctly, 404 for RECEPTION, 400 for a missing required surface, one
 * TOOTH_FINDING_ADDED audit row with before_state:null.
 */
class ToothFindingControllerTest extends PostgresIntegrationTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"Findings","dateOfBirth":"1990-01-15",
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

  private String cariesEntryId() {
    return jdbcTemplate.queryForObject(
        "SELECT id::text FROM diagnosis_catalog_entry WHERE code = 'K02.1'", String.class);
  }

  @Test
  void doctor_canAddFinding_andItWritesAnAuditEntryWithNullBeforeState() throws Exception {
    UUID id = createPatient("90011525069");
    UUID actorId = UUID.randomUUID();
    long before = countEntries("TOOTH_FINDING_ADDED");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings")
                .with(user(actorId.toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fdiNumber":36,"diagnosisCatalogEntryId":"%s","surfaces":["OCCLUSAL_INCISAL"],
                     "diagnosisDate":"2026-08-30"}
                    """
                        .formatted(cariesEntryId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recordStatus").value("CURRENT"))
        .andExpect(jsonPath("$.clinicalStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.authorRole").value("DOCTOR"))
        .andExpect(jsonPath("$.fdiNumber").value(36));

    assertThat(countEntries("TOOTH_FINDING_ADDED")).isEqualTo(before + 1);
    String beforeState =
        jdbcTemplate.queryForObject(
            "SELECT before_state::text FROM audit_log_entry WHERE event_type ="
                + " 'TOOTH_FINDING_ADDED'::audit_event_type ORDER BY id DESC LIMIT 1",
            String.class);
    assertThat(beforeState).isNull();
  }

  @Test
  void assistant_canAddFinding_withAuthorRoleAssistant() throws Exception {
    UUID id = createPatient("90011525076");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings")
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fdiNumber":36,"diagnosisCatalogEntryId":"%s","surfaces":["MESIAL"],
                     "diagnosisDate":"2026-08-30"}
                    """
                        .formatted(cariesEntryId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.authorRole").value("ASSISTANT"));
  }

  @Test
  void reception_isDenied404_onAddFinding() throws Exception {
    UUID id = createPatient("90011525083");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fdiNumber":36,"diagnosisCatalogEntryId":"%s","diagnosisDate":"2026-08-30"}
                    """
                        .formatted(cariesEntryId())))
        .andExpect(status().isNotFound());
  }

  @Test
  void missingRequiredSurface_returns400() throws Exception {
    UUID id = createPatient("90011525090");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fdiNumber":36,"diagnosisCatalogEntryId":"%s","diagnosisDate":"2026-08-30"}
                    """
                        .formatted(cariesEntryId())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void closeThenAttemptToCloseAgain_returns409OnSecondAttempt() throws Exception {
    UUID id = createPatient("90011527009");
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
                        {"fdiNumber":36,"diagnosisCatalogEntryId":"%s","surfaces":["MESIAL"],
                         "diagnosisDate":"2026-01-01"}
                        """
                            .formatted(cariesEntryId())))
            .andExpect(status().isCreated())
            .andReturn();
    String findingId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
    long before = countEntries("TOOTH_FINDING_ADDED");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings/" + findingId + "/close")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"resolvedDate\":\"2026-08-30\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.supersedesFindingId").value(findingId))
        .andExpect(jsonPath("$.clinicalStatus").value("RESOLVED"));

    assertThat(countEntries("TOOTH_FINDING_ADDED")).isEqualTo(before + 1);
    String beforeState =
        jdbcTemplate.queryForObject(
            "SELECT before_state::text FROM audit_log_entry WHERE event_type ="
                + " 'TOOTH_FINDING_ADDED'::audit_event_type ORDER BY id DESC LIMIT 1",
            String.class);
    assertThat(beforeState).isNotNull();

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings/" + findingId + "/close")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"resolvedDate\":\"2026-08-31\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void assistant_canCorrectADoctorAuthoredFinding_andHistoryShowsBothAuthors() throws Exception {
    UUID id = createPatient("90011527016");
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
                        {"fdiNumber":36,"diagnosisCatalogEntryId":"%s","surfaces":["MESIAL"],
                         "diagnosisDate":"2026-01-01"}
                        """
                            .formatted(cariesEntryId())))
            .andExpect(status().isCreated())
            .andReturn();
    String findingId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings/" + findingId + "/correct")
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fdiNumber":36,"diagnosisCatalogEntryId":"%s","surfaces":["DISTAL"],
                     "diagnosisDate":"2026-01-01"}
                    """
                        .formatted(cariesEntryId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.authorRole").value("ASSISTANT"));

    mockMvc
        .perform(
            get("/patients/" + id + "/tooth-chart/positions/36/history")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].authorRole").value("DOCTOR"))
        .andExpect(jsonPath("$[1].authorRole").value("ASSISTANT"));
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
