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

/**
 * T007/T024/T037 — {@code GET/POST /patients/{id}/{allergies,medications,chronic-conditions}[/history]}
 * per contracts/patient-api.yaml (FR-001..FR-012). Mirrors {@code ToothChartApiTest}'s structure:
 * 200 for DOCTOR/ASSISTANT reads, DOCTOR-only writes, 404 for RECEPTION, and every operation
 * writes the expected audit_log_entry row (metadata.entryType discriminator, research.md #2).
 */
class MedicalHistoryControllerTest extends PostgresIntegrationTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"Historia","dateOfBirth":"1990-01-15",
       "pesel":"%s","addressStreet":"Polna","addressBuildingNo":"12A",
       "addressPostalCode":"00-001","addressCity":"Warszawa"}
      """;

  private UUID createPatient(String pesel) throws Exception {
    var result =
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

  private long countEntries(String eventType) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log_entry WHERE event_type = '"
                + eventType
                + "'::audit_event_type",
            Long.class);
    return count == null ? 0 : count;
  }

  // ---------------------------------------------------------------------------------------
  // Allergies (US1)
  // ---------------------------------------------------------------------------------------

  @Test
  void doctor_canAddAndReadAllergies_andItWritesAuditEntries() throws Exception {
    UUID id = createPatient("91011502001");
    long addedBefore = countEntries("MEDICAL_HISTORY_ENTRY_ADDED");
    long viewedBefore = countEntries("MEDICAL_HISTORY_ENTRY_VIEWED");

    mockMvc
        .perform(
            post("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"substance":"Penicylina","reactionType":"Anafilaksja","severity":"CRITICAL"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.substance").value("Penicylina"))
        .andExpect(jsonPath("$.recordStatus").value("CURRENT"));

    mockMvc
        .perform(
            get("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].severity").value("CRITICAL"));

    assertThat(countEntries("MEDICAL_HISTORY_ENTRY_ADDED")).isEqualTo(addedBefore + 1);
    assertThat(countEntries("MEDICAL_HISTORY_ENTRY_VIEWED")).isEqualTo(viewedBefore + 1);
  }

  @Test
  void emptyPatient_returnsEmptyArray_notAnError() throws Exception {
    UUID id = createPatient("91011502018");

    mockMvc
        .perform(
            get("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void assistant_canReadAllergies_butCannotAddThem() throws Exception {
    UUID id = createPatient("91011502025");

    mockMvc
        .perform(
            get("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"substance":"Lateks","reactionType":"Wysypka","severity":"MODERATE"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void reception_isDenied404_onAllergiesReadAndWrite() throws Exception {
    UUID id = createPatient("91011502032");

    mockMvc
        .perform(
            get("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"substance":"Lateks","reactionType":"Wysypka","severity":"MODERATE"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void reception_seesFactOnlyCriticalAlert_onBasicDataRead() throws Exception {
    UUID id = createPatient("91011502049");

    mockMvc
        .perform(
            post("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"substance":"Penicylina","reactionType":"Anafilaksja","severity":"CRITICAL"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/patients/" + id)
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasCriticalAllergyAlert").value(true))
        .andExpect(jsonPath("$.pesel").value("91011502049"));
  }

  @Test
  void correction_hidesSupersededFromDefaultView_butHistoryShowsBoth() throws Exception {
    UUID id = createPatient("91011502056");

    var createResult =
        mockMvc
            .perform(
                post("/patients/" + id + "/allergies")
                    .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                    .cookie(CSRF_TOKEN_COOKIE)
                    .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"substance":"Penicylina","reactionType":"Anafilaksja","severity":"CRITICAL"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    String originalId =
        JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"substance":"Penicylina","reactionType":"Anafilaksja","severity":"MODERATE",
                     "supersedesEntryId":"%s"}
                    """
                        .formatted(originalId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/patients/" + id + "/allergies")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].severity").value("MODERATE"));

    long historyBefore = countEntries("MEDICAL_HISTORY_HISTORY_VIEWED");
    mockMvc
        .perform(
            get("/patients/" + id + "/allergies/history")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    assertThat(countEntries("MEDICAL_HISTORY_HISTORY_VIEWED")).isEqualTo(historyBefore + 1);
  }
}
