package com.dentalclinic.patient.toothchart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.dentalclinic.patient.record.PatientCreateService;
import com.dentalclinic.patient.record.PatientRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T050 — {@link ToothFindingService#addFinding} requires ≥1 surface for SURFACE-scope entries and
 * forbids surfaces otherwise (FR-022/FR-023), requires freeTextDescription iff requiresFreeText
 * (FR-011a), rejects a diagnosisDate in the future or before the patient's date of birth (FR-036),
 * and resolves each "inne rozpoznanie" row to its own AnatomicalScope (D1).
 */
class ToothFindingServiceTest extends PostgresIntegrationTestBase {

  @Autowired private PatientCreateService patientCreateService;
  @Autowired private ToothFindingService toothFindingService;
  @Autowired private DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository;
  @Autowired private ToothFindingRepository toothFindingRepository;
  @Autowired private ToothPositionRepository toothPositionRepository;
  @Autowired private ToothChartRepository toothChartRepository;

  private PatientRecord createAdultPatient(String pesel) {
    return patientCreateService.create(
        "Jan",
        "Findings",
        LocalDate.of(1990, 1, 1),
        pesel,
        "Polna",
        "1",
        "00-001",
        "Warszawa",
        UUID.randomUUID());
  }

  private DiagnosisCatalogEntry entryByCode(String code) {
    return diagnosisCatalogEntryRepository.findByCode(code).orElseThrow();
  }

  @Test
  void addFinding_requiresAtLeastOneSurface_forSurfaceScopeEntry() {
    PatientRecord patient = createAdultPatient("90011525007");
    DiagnosisCatalogEntry caries = entryByCode("K02.1"); // SURFACE scope

    assertThatThrownBy(
            () ->
                toothFindingService.addFinding(
                    patient.getId(),
                    36,
                    caries.getId(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 8, 30),
                    UUID.randomUUID(),
                    FindingAuthorRole.DOCTOR))
        .isInstanceOf(InvalidFindingException.class);
  }

  @Test
  void addFinding_forbidsSurfaces_forNonSurfaceScopeEntry() {
    PatientRecord patient = createAdultPatient("90011525014");
    DiagnosisCatalogEntry pulpitis = entryByCode("K04.0i"); // WHOLE_TOOTH scope

    assertThatThrownBy(
            () ->
                toothFindingService.addFinding(
                    patient.getId(),
                    36,
                    pulpitis.getId(),
                    List.of(ToothSurface.MESIAL),
                    null,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 8, 30),
                    UUID.randomUUID(),
                    FindingAuthorRole.DOCTOR))
        .isInstanceOf(InvalidFindingException.class);
  }

  @Test
  void addFinding_succeeds_withValidSurfaceScopeEntry() {
    PatientRecord patient = createAdultPatient("90011525021");
    DiagnosisCatalogEntry caries = entryByCode("K02.1");

    ToothFinding finding =
        toothFindingService.addFinding(
            patient.getId(),
            36,
            caries.getId(),
            List.of(ToothSurface.OCCLUSAL_INCISAL, ToothSurface.MESIAL),
            null,
            null,
            null,
            "Ubytek sięgający zębiny.",
            LocalDate.of(2026, 8, 30),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);

    assertThat(finding.getClinicalStatus()).isEqualTo(FindingClinicalStatus.ACTIVE);
    assertThat(finding.getRecordStatus()).isEqualTo(FindingRecordStatus.CURRENT);
    assertThat(finding.getSurfaces())
        .containsExactlyInAnyOrder(ToothSurface.OCCLUSAL_INCISAL, ToothSurface.MESIAL);
  }

  @Test
  void addFinding_requiresFreeTextDescription_forOtherDiagnosisEntries() {
    PatientRecord patient = createAdultPatient("90011525038");
    DiagnosisCatalogEntry other = entryByCode("OTHER_WHOLE_TOOTH");

    assertThatThrownBy(
            () ->
                toothFindingService.addFinding(
                    patient.getId(),
                    36,
                    other.getId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 8, 30),
                    UUID.randomUUID(),
                    FindingAuthorRole.DOCTOR))
        .isInstanceOf(InvalidFindingException.class);

    // succeeds once free text is supplied, and resolves to WHOLE_TOOTH scope from the catalog row
    ToothFinding finding =
        toothFindingService.addFinding(
            patient.getId(),
            36,
            other.getId(),
            null,
            null,
            null,
            "Nietypowa zmiana barwy szkliwa.",
            null,
            LocalDate.of(2026, 8, 30),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);
    assertThat(finding.getFreeTextDescription()).isEqualTo("Nietypowa zmiana barwy szkliwa.");
  }

  @Test
  void addFinding_eachOtherRow_resolvesToItsOwnAnatomicalScope() {
    assertThat(entryByCode("OTHER_SURFACE").getAnatomicalScope())
        .isEqualTo(AnatomicalScope.SURFACE);
    assertThat(entryByCode("OTHER_WHOLE_TOOTH").getAnatomicalScope())
        .isEqualTo(AnatomicalScope.WHOLE_TOOTH);
    assertThat(entryByCode("OTHER_ROOT_PERIAPICAL").getAnatomicalScope())
        .isEqualTo(AnatomicalScope.ROOT_PERIAPICAL);
    assertThat(entryByCode("OTHER_PERIODONTIUM").getAnatomicalScope())
        .isEqualTo(AnatomicalScope.PERIODONTIUM);
  }

  @Test
  void addFinding_rejectsFutureDiagnosisDate() {
    PatientRecord patient = createAdultPatient("90011525045");
    DiagnosisCatalogEntry caries = entryByCode("K02.1");

    assertThatThrownBy(
            () ->
                toothFindingService.addFinding(
                    patient.getId(),
                    36,
                    caries.getId(),
                    List.of(ToothSurface.MESIAL),
                    null,
                    null,
                    null,
                    null,
                    LocalDate.now().plusDays(1),
                    UUID.randomUUID(),
                    FindingAuthorRole.DOCTOR))
        .isInstanceOf(InvalidFindingException.class);
  }

  @Test
  void addFinding_rejectsDateBeforePatientDateOfBirth() {
    PatientRecord patient = createAdultPatient("90011525052");
    DiagnosisCatalogEntry caries = entryByCode("K02.1");

    assertThatThrownBy(
            () ->
                toothFindingService.addFinding(
                    patient.getId(),
                    36,
                    caries.getId(),
                    List.of(ToothSurface.MESIAL),
                    null,
                    null,
                    null,
                    null,
                    LocalDate.of(1980, 1, 1),
                    UUID.randomUUID(),
                    FindingAuthorRole.DOCTOR))
        .isInstanceOf(InvalidFindingException.class);
  }

  /**
   * T080 — closeFinding/correctFinding insert a new CURRENT row and flip the original to SUPERSEDED
   * atomically; a second attempt to close/correct the same already-superseded original fails
   * (research.md D7).
   */
  @Test
  void closeFinding_supersedesOriginal_andSetsResolvedDateAndClinicalStatus() {
    PatientRecord patient = createAdultPatient("90011526008");
    DiagnosisCatalogEntry caries = entryByCode("K02.1");
    ToothFinding original =
        toothFindingService.addFinding(
            patient.getId(),
            36,
            caries.getId(),
            List.of(ToothSurface.MESIAL),
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);

    ToothFinding closed =
        toothFindingService.closeFinding(
            patient.getId(), original.getId(), LocalDate.of(2026, 8, 30), null, UUID.randomUUID());

    assertThat(closed.getSupersedesFindingId()).isEqualTo(original.getId());
    assertThat(closed.getClinicalStatus()).isEqualTo(FindingClinicalStatus.RESOLVED);
    assertThat(closed.getResolvedDate()).isEqualTo(LocalDate.of(2026, 8, 30));
    assertThat(closed.getRecordStatus()).isEqualTo(FindingRecordStatus.CURRENT);

    ToothFinding reloadedOriginal = toothFindingRepository.findById(original.getId()).orElseThrow();
    assertThat(reloadedOriginal.getRecordStatus()).isEqualTo(FindingRecordStatus.SUPERSEDED);
  }

  @Test
  void closingAnAlreadySupersededFinding_fails() {
    PatientRecord patient = createAdultPatient("90011526015");
    DiagnosisCatalogEntry caries = entryByCode("K02.1");
    ToothFinding original =
        toothFindingService.addFinding(
            patient.getId(),
            36,
            caries.getId(),
            List.of(ToothSurface.MESIAL),
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);
    toothFindingService.closeFinding(
        patient.getId(), original.getId(), LocalDate.of(2026, 8, 30), null, UUID.randomUUID());

    assertThatThrownBy(
            () ->
                toothFindingService.closeFinding(
                    patient.getId(),
                    original.getId(),
                    LocalDate.of(2026, 8, 31),
                    null,
                    UUID.randomUUID()))
        .isInstanceOf(FindingConflictException.class);
  }

  @Test
  void correctFinding_insertsNewCurrentRow_withCorrectedFields_andKeepsBothLinkedInHistory() {
    PatientRecord patient = createAdultPatient("90011526022");
    DiagnosisCatalogEntry caries = entryByCode("K02.1");
    ToothFinding original =
        toothFindingService.addFinding(
            patient.getId(),
            36,
            caries.getId(),
            List.of(ToothSurface.MESIAL),
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);

    ToothFinding corrected =
        toothFindingService.correctFinding(
            patient.getId(),
            original.getId(),
            caries.getId(),
            List.of(ToothSurface.DISTAL),
            null,
            null,
            null,
            "Skorygowano powierzchnię.",
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            FindingAuthorRole.ASSISTANT);

    assertThat(corrected.getSupersedesFindingId()).isEqualTo(original.getId());
    assertThat(corrected.getSurfaces()).containsExactly(ToothSurface.DISTAL);

    var history =
        toothFindingRepository.findByToothPositionIdOrderByCreatedAtAsc(
            original.getToothPositionId());
    assertThat(history).hasSize(2);
    assertThat(history)
        .extracting(ToothFinding::getId)
        .containsExactlyInAnyOrder(original.getId(), corrected.getId());
  }

  /**
   * T112 — addFindingsBulk creates one independent ToothFinding per applicable position inside a
   * single transaction, skips inapplicable positions with a human-readable reason, and never fails
   * the whole call (FR-004a, US6 scenario 3/4).
   */
  @Test
  void addFindingsBulk_createsOneFindingPerApplicablePosition_andSkipsTheRestWithAReason() {
    PatientRecord patient = createAdultPatient("90011560011");
    DiagnosisCatalogEntry caries = entryByCode("K02.1"); // SURFACE scope

    // 99 isn't a real FDI number on this chart, so addFinding rejects it as
    // PatientNotFoundException — exercising the skip path without failing the whole call.
    ToothFindingService.BulkResult result =
        toothFindingService.addFindingsBulk(
            patient.getId(),
            List.of(11, 12, 13, 99),
            caries.getId(),
            List.of(ToothSurface.MESIAL),
            null,
            null,
            null,
            LocalDate.of(2026, 8, 30),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);

    assertThat(result.created()).hasSize(3);
    assertThat(result.created())
        .extracting(f -> toothFindingService.fdiNumberOf(f))
        .containsExactlyInAnyOrder(11, 12, 13);
    assertThat(result.skipped()).hasSize(1);
    assertThat(result.skipped().get(0).fdiNumber()).isEqualTo(99);
    assertThat(result.skipped().get(0).reason()).isNotBlank();
  }

  @Test
  void addFindingsBulk_neverFailsTheWholeCall_whenSomePositionsAreInapplicable() {
    PatientRecord patient = createAdultPatient("90011560028");
    DiagnosisCatalogEntry caries = entryByCode("K02.1");

    ToothFindingService.BulkResult result =
        toothFindingService.addFindingsBulk(
            patient.getId(),
            List.of(0, 200),
            caries.getId(),
            List.of(ToothSurface.MESIAL),
            null,
            null,
            null,
            LocalDate.of(2026, 8, 30),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);

    assertThat(result.created()).isEmpty();
    assertThat(result.skipped()).hasSize(2);
  }
}
