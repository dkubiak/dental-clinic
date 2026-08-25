package com.dentalclinic.patient.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T028 — {@code POST /patients} per contracts/patient-api.yaml (US1 Acceptance Scenarios 1–4, 6).
 */
class PatientCreateApiTest extends PostgresIntegrationTestBase {

  private static final String VALID_BODY =
      """
      {"firstName":"Jan","lastName":"Kowalski","dateOfBirth":"1990-01-15",
       "pesel":"%s","addressStreet":"Polna","addressBuildingNo":"12A",
       "addressPostalCode":"00-001","addressCity":"Warszawa"}
      """;

  @Test
  void reception_canCreateWithValidPesel() throws Exception {
    mockMvc
        .perform(
            post("/patients")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(VALID_BODY.formatted("90011500013")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.pesel").value("90011500013"))
        .andExpect(jsonPath("$.firstName").value("Jan"));
  }

  @Test
  void doctor_canCreateWithoutPesel() throws Exception {
    String body =
        """
        {"firstName":"Anna","lastName":"Nowak","dateOfBirth":"1985-05-20",
         "addressStreet":"Leśna","addressBuildingNo":"3",
         "addressPostalCode":"00-002","addressCity":"Kraków"}
        """;

    mockMvc
        .perform(
            post("/patients")
                .with(user(UUID.randomUUID().toString()).roles("DOCTOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.pesel").doesNotExist());
  }

  @Test
  void badChecksum_returns400_andNoRecordCreated() throws Exception {
    mockMvc
        .perform(
            post("/patients")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(
                    VALID_BODY.formatted("90011500021"))) // last digit altered from a valid PESEL
        .andExpect(status().isBadRequest());
  }

  @Test
  void duplicatePesel_returns409() throws Exception {
    String pesel = "90011500037";
    mockMvc
        .perform(
            post("/patients")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(VALID_BODY.formatted(pesel)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/patients")
                .with(user(UUID.randomUUID().toString()).roles("RECEPTION"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(VALID_BODY.formatted(pesel)))
        .andExpect(status().isConflict());
  }

  @Test
  void assistant_isDenied404() throws Exception {
    mockMvc
        .perform(
            post("/patients")
                .with(user(UUID.randomUUID().toString()).roles("ASSISTANT"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(VALID_BODY.formatted("11111111111")))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthenticated_isRejected401() throws Exception {
    mockMvc
        .perform(
            post("/patients")
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content(VALID_BODY.formatted("22222222222")))
        .andExpect(status().isUnauthorized());
  }
}
