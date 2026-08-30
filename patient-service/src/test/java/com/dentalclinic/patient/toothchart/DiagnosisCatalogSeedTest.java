package com.dentalclinic.patient.toothchart;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T029 — the Flyway-seeded {@code diagnosis_catalog_entry} table (V4 migration) satisfies FR-015
 * (every code present, unique), FR-011a (exactly four requiresFreeText rows, one per {@link
 * AnatomicalScope} value), and FR-014/FR-016 (every row's anatomicalScope/layer is a valid
 * classification).
 */
class DiagnosisCatalogSeedTest extends PostgresIntegrationTestBase {

  @Autowired private DiagnosisCatalogEntryRepository repository;

  private static final List<String> FR015_CODES =
      List.of(
          "K02.0a", "K02.0b", "K02.1", "K02.1d", "K02.1s", "K02.2",
          "K04.0r", "K04.0i", "K04.1",
          "K04.4", "K04.7", "K04.8",
          "S02.51", "S02.53", "K03.81", "S02.52",
          "K03.0", "K03.1", "K03.2", "K03.19", "K03.8",
          "K05.1", "K05.3", "K06.0", "K03.6", "K05.31",
          "K01.0", "K00.6", "K07.3", "K00.0",
          "EXTR",
          "FILL", "FILLT", "SEAL", "ENDO", "POST", "CROWN", "VENEER", "PONTIC", "IMPL", "ABUT");

  @Test
  void catalog_containsEveryFr015Code_withUniqueCodes() {
    List<DiagnosisCatalogEntry> all = repository.findAll();
    Set<String> codes = all.stream().map(DiagnosisCatalogEntry::getCode).collect(Collectors.toSet());

    assertThat(codes).hasSize(all.size()); // uniqueness
    assertThat(codes).containsAll(FR015_CODES);
    assertThat(all.size()).isGreaterThanOrEqualTo(40);
  }

  @Test
  void catalog_hasExactlyFourRequiresFreeTextRows_onePerAnatomicalScope() {
    List<DiagnosisCatalogEntry> freeTextRows =
        repository.findAll().stream().filter(DiagnosisCatalogEntry::isRequiresFreeText).toList();

    assertThat(freeTextRows).hasSize(4);
    Set<AnatomicalScope> scopes =
        freeTextRows.stream().map(DiagnosisCatalogEntry::getAnatomicalScope).collect(Collectors.toSet());
    assertThat(scopes)
        .containsExactlyInAnyOrder(
            AnatomicalScope.SURFACE,
            AnatomicalScope.WHOLE_TOOTH,
            AnatomicalScope.ROOT_PERIAPICAL,
            AnatomicalScope.PERIODONTIUM);
  }

  @Test
  void catalog_diseaseCodes_areLayerDiagnosis_restorationCodesAreLayerExistingState() {
    var byCode = repository.findAll().stream()
        .collect(Collectors.toMap(DiagnosisCatalogEntry::getCode, e -> e));

    assertThat(byCode.get("K02.1").getLayer()).isEqualTo(FindingLayer.DIAGNOSIS);
    assertThat(byCode.get("K02.1").getCategory()).isEqualTo(DiagnosisCategory.HARD_TISSUE);
    assertThat(byCode.get("K02.1").getAnatomicalScope()).isEqualTo(AnatomicalScope.SURFACE);

    assertThat(byCode.get("FILL").getLayer()).isEqualTo(FindingLayer.EXISTING_STATE);
    assertThat(byCode.get("FILL").getCategory())
        .isEqualTo(DiagnosisCategory.POST_TREATMENT_RESTORATION);

    assertThat(byCode.get("EXTR").getLayer()).isEqualTo(FindingLayer.EXISTING_STATE);
    assertThat(byCode.get("EXTR").isAllowedForMissingTooth()).isTrue();

    assertThat(byCode.get("K05.3").getAnatomicalScope()).isEqualTo(AnatomicalScope.PERIODONTIUM);
    assertThat(byCode.get("K05.3").getSeverityOptions()).isNotNull();
  }
}
