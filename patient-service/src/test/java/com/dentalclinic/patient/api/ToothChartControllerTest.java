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

  @Test
  void positionHistory_returnsCurrentResolvedAndSuperseded_inChronologicalOrder_withDetailMetadata()
      throws Exception {
    UUID id = createPatient("90011502046");
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

    MvcResult correctResult =
        mockMvc
            .perform(
                post("/patients/" + id + "/tooth-chart/findings/" + originalId + "/correct")
                    .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                    .cookie(CSRF_TOKEN_COOKIE)
                    .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"fdiNumber":26,"diagnosisCatalogEntryId":"%s","surfaces":["DISTAL"],
                         "diagnosisDate":"2026-01-01"}
                        """
                            .formatted(cariesEntryId)))
            .andExpect(status().isCreated())
            .andReturn();
    String correctedId = JsonPath.read(correctResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/findings/" + correctedId + "/close")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"resolvedDate\":\"2026-08-30\"}"))
        .andExpect(status().isCreated());

    long before = countEntries("TOOTH_CHART_VIEWED");

    mockMvc
        .perform(
            get("/patients/" + id + "/tooth-chart/positions/26/history")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].id").value(originalId))
        .andExpect(jsonPath("$[0].recordStatus").value("SUPERSEDED"))
        .andExpect(jsonPath("$[1].id").value(correctedId))
        .andExpect(jsonPath("$[1].recordStatus").value("SUPERSEDED"))
        .andExpect(jsonPath("$[2].recordStatus").value("CURRENT"))
        .andExpect(jsonPath("$[2].clinicalStatus").value("RESOLVED"))
        .andExpect(jsonPath("$[2].supersedesFindingId").value(correctedId));

    assertThat(countEntries("TOOTH_CHART_VIEWED")).isEqualTo(before + 1);
    String metadata =
        jdbcTemplate.queryForObject(
            "SELECT metadata::text FROM audit_log_entry WHERE event_type ="
                + " 'TOOTH_CHART_VIEWED'::audit_event_type ORDER BY id DESC LIMIT 1",
            String.class);
    assertThat(metadata).contains("\"detail\": \"position-history\"");
  }

  /**
   * T092 — {@code PATCH .../positions/{fdi}/presence}: RBAC (DOCTOR/ASSISTANT only) and one
   * TOOTH_POSITION_PRESENCE_CHANGED audit row per successful write.
   */
  @Test
  void doctor_canChangePresence_andItWritesAnAuditEntry() throws Exception {
    UUID id = createPatient("90011502053");
    long before = countEntries("TOOTH_POSITION_PRESENCE_CHANGED");

    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/positions/36/presence")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"presence\":\"EXTRACTED\",\"presenceDate\":\"2026-08-30\",\"expectedVersion\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.presence").value("EXTRACTED"))
        .andExpect(jsonPath("$.version").value(1));

    assertThat(countEntries("TOOTH_POSITION_PRESENCE_CHANGED")).isEqualTo(before + 1);
  }

  @Test
  void reception_isDenied404_onChangePresence() throws Exception {
    UUID id = createPatient("90011502060");

    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/positions/36/presence")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"presence\":\"EXTRACTED\",\"expectedVersion\":0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void changePresence_withStaleExpectedVersion_returns409() throws Exception {
    UUID id = createPatient("90011502077");

    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/positions/36/presence")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"presence\":\"EXTRACTED\",\"expectedVersion\":7}"))
        .andExpect(status().isConflict());
  }

  /**
   * T105 — {@code PATCH .../tooth-chart/dentition-mode}: succeeds for DOCTOR/ASSISTANT, persists
   * across a subsequent GET, audited as DENTITION_MODE_CHANGED.
   */
  @Test
  void doctor_canChangeDentitionMode_andItPersistsAndIsAudited() throws Exception {
    UUID id = createPatient("90011505001");
    long before = countEntries("DENTITION_MODE_CHANGED");

    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/dentition-mode")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"dentitionMode\":\"MIXED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dentitionMode").value("MIXED"));

    assertThat(countEntries("DENTITION_MODE_CHANGED")).isEqualTo(before + 1);

    mockMvc
        .perform(
            get("/patients/" + id + "/tooth-chart")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dentitionMode").value("MIXED"));
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
