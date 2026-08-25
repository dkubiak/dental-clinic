package com.dentalclinic.patient.api;

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
import org.springframework.test.web.servlet.MvcResult;

/**
 * T051 — {@code GET /patients/{id}/visit-history} per contracts/patient-api.yaml (FR-004): {@code
 * 200} with an empty array for RECEPTION/DOCTOR, {@code 404} for ASSISTANT/ADMINISTRATOR (US3
 * Acceptance Scenarios 1–2 — the future visits module is out of scope, so this is always empty).
 */
class VisitHistoryApiTest extends PostgresIntegrationTestBase {

  private static final String CREATE_BODY =
      """
      {"firstName":"Jan","lastName":"VisitHistory","dateOfBirth":"1990-01-15",
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
  void receptionAndDoctor_seeAnEmptyVisitHistory() throws Exception {
    UUID id = createPatient("90011502084");

    for (String role : new String[] {"RECEPTION", "DOCTOR"}) {
      mockMvc
          .perform(
              get("/patients/" + id + "/visit-history")
                  .with(user(UUID.randomUUID().toString()).roles(role)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    }
  }

  @Test
  void assistant_isDenied404() throws Exception {
    UUID id = createPatient("90011502091");

    mockMvc
        .perform(
            get("/patients/" + id + "/visit-history")
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT")))
        .andExpect(status().isNotFound());
  }

  @Test
  void administrator_isDenied404() throws Exception {
    UUID id = createPatient("90011502107");

    mockMvc
        .perform(
            get("/patients/" + id + "/visit-history")
                .with(user(UUID.randomUUID().toString()).roles("ADMINISTRATOR")))
        .andExpect(status().isNotFound());
  }
}
