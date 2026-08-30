package com.dentalclinic.patient.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * T092 — {@code POST/PATCH/DELETE .../positions/{fdi}/canals[/{id}]} per
 * contracts/patient-api.yaml (FR-065/FR-066/FR-068). RBAC (DOCTOR/ASSISTANT only) and audit rows
 * (ROOT_CANAL_ADDED/ROOT_CANAL_CHANGED/ROOT_CANAL_REMOVED).
 */
class RootCanalControllerTest extends PostgresIntegrationTestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"Canals","dateOfBirth":"1990-01-15",
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

  private long countEntries(String eventType) {
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit_log_entry WHERE event_type = '"
                + eventType
                + "'::audit_event_type",
            Long.class);
    return count == null ? 0 : count;
  }

  @Test
  void doctor_canAddRenameAndRemoveACanal_eachAudited() throws Exception {
    UUID id = createPatient("90011532007");
    long addedBefore = countEntries("ROOT_CANAL_ADDED");

    MvcResult addResult =
        mockMvc
            .perform(
                post("/patients/" + id + "/tooth-chart/positions/36/canals")
                    .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                    .cookie(CSRF_TOKEN_COOKIE)
                    .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                    .contentType(APPLICATION_JSON)
                    .content("{\"name\":\"MB\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("MB"))
            .andExpect(jsonPath("$.state").value("NEEDS_TREATMENT"))
            .andReturn();
    String canalId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.id");
    assertThat(countEntries("ROOT_CANAL_ADDED")).isEqualTo(addedBefore + 1);

    long changedBefore = countEntries("ROOT_CANAL_CHANGED");
    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/positions/36/canals/" + canalId)
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"state\":\"TREATED\",\"expectedVersion\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("TREATED"));
    assertThat(countEntries("ROOT_CANAL_CHANGED")).isEqualTo(changedBefore + 1);

    long removedBefore = countEntries("ROOT_CANAL_REMOVED");
    mockMvc
        .perform(
            delete("/patients/" + id + "/tooth-chart/positions/36/canals/" + canalId)
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andExpect(status().isNoContent());
    assertThat(countEntries("ROOT_CANAL_REMOVED")).isEqualTo(removedBefore + 1);
  }

  @Test
  void reception_isDenied404_onAddCanal() throws Exception {
    UUID id = createPatient("90011532014");

    mockMvc
        .perform(
            post("/patients/" + id + "/tooth-chart/positions/36/canals")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"MB\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void updateCanal_withStaleExpectedVersion_returns409() throws Exception {
    UUID id = createPatient("90011532021");
    MvcResult addResult =
        mockMvc
            .perform(
                post("/patients/" + id + "/tooth-chart/positions/36/canals")
                    .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                    .cookie(CSRF_TOKEN_COOKIE)
                    .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                    .contentType(APPLICATION_JSON)
                    .content("{\"name\":\"MB\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String canalId = JsonPath.read(addResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            patch("/patients/" + id + "/tooth-chart/positions/36/canals/" + canalId)
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"state\":\"TREATED\",\"expectedVersion\":5}"))
        .andExpect(status().isConflict());
  }
}
