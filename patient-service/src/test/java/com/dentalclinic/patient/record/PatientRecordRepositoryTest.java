package com.dentalclinic.patient.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * T022 — save/find round-trip, and the partial-unique-PESEL constraint (V1__patient_record.sql,
 * FR-003) is enforced at the DB level, not just in application code.
 */
class PatientRecordRepositoryTest extends PostgresIntegrationTestBase {

  @Autowired private PatientRecordRepository repository;

  @Test
  void saveAndFind_roundTrips() {
    PatientRecord record =
        new PatientRecord(
            UUID.randomUUID(),
            "Jan",
            "Kowalski",
            LocalDate.of(1990, 1, 15),
            "90011512345",
            "Polna",
            "12A",
            "00-001",
            "Warszawa",
            UUID.randomUUID());

    PatientRecord saved = repository.save(record);
    repository.flush();

    PatientRecord found = repository.findById(saved.getId()).orElseThrow();
    assertThat(found.getFirstName()).isEqualTo("Jan");
    assertThat(found.getLastName()).isEqualTo("Kowalski");
    assertThat(found.getPesel()).isEqualTo("90011512345");
  }

  @Test
  void save_withoutPesel_succeeds() {
    PatientRecord record =
        new PatientRecord(
            UUID.randomUUID(),
            "Anna",
            "Nowak",
            LocalDate.of(1985, 5, 20),
            null,
            "Leśna",
            "3",
            "00-002",
            "Kraków",
            UUID.randomUUID());

    PatientRecord saved = repository.save(record);
    repository.flush();

    assertThat(repository.findById(saved.getId())).isPresent();
  }

  @Test
  void duplicatePesel_violatesDatabaseConstraint() {
    String sharedPesel = "44051401359";
    PatientRecord first =
        new PatientRecord(
            UUID.randomUUID(),
            "Piotr",
            "Wiśniewski",
            LocalDate.of(1980, 3, 3),
            sharedPesel,
            "Krótka",
            "1",
            "00-003",
            "Poznań",
            UUID.randomUUID());
    repository.saveAndFlush(first);

    PatientRecord duplicate =
        new PatientRecord(
            UUID.randomUUID(),
            "Paweł",
            "Zieliński",
            LocalDate.of(1982, 7, 7),
            sharedPesel,
            "Długa",
            "2",
            "00-004",
            "Gdańsk",
            UUID.randomUUID());

    assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByPesel_returnsExistingRecord() {
    String pesel = "72011899999"; // no valid checksum needed — DB has no format constraint itself
    PatientRecord record =
        new PatientRecord(
            UUID.randomUUID(),
            "Ewa",
            "Kamińska",
            LocalDate.of(1972, 1, 18),
            pesel,
            "Rynek",
            "5",
            "00-005",
            "Wrocław",
            UUID.randomUUID());
    repository.saveAndFlush(record);

    assertThat(repository.findByPesel(pesel)).isPresent();
    assertThat(repository.findByPesel("00000000000")).isEmpty();
  }
}
