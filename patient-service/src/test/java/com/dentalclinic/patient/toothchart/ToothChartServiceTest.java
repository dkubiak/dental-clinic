package com.dentalclinic.patient.toothchart;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.dentalclinic.patient.record.PatientCreateService;
import com.dentalclinic.patient.record.PatientRecord;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T030 — {@link ToothChartService#getChart} returns 32 PERMANENT positions, all PRESENT, zero
 * findings, for a fresh adult patient (US1 Acceptance Scenario 1).
 */
class ToothChartServiceTest extends PostgresIntegrationTestBase {

  @Autowired private PatientCreateService patientCreateService;
  @Autowired private ToothChartService toothChartService;

  private PatientRecord createAdultPatient(String pesel) {
    return patientCreateService.create(
        "Jan",
        "Odontogram",
        LocalDate.of(1990, 1, 1),
        pesel,
        "Polna",
        "1",
        "00-001",
        "Warszawa",
        UUID.randomUUID());
  }

  @Test
  void getChart_returnsThirtyTwoPresentPermanentPositions_withNoFindings_forFreshAdultPatient() {
    PatientRecord patient = createAdultPatient("90011502503");

    ToothChartService.ChartView view =
        toothChartService.getChart(patient.getId(), UUID.randomUUID());

    assertThat(view.chart().getDentitionMode()).isEqualTo(DentitionMode.PERMANENT);
    assertThat(view.positions()).hasSize(52);

    long permanentCount =
        view.positions().stream().filter(p -> p.dentitionType() == DentitionType.PERMANENT).count();
    assertThat(permanentCount).isEqualTo(32);
    assertThat(view.positions()).allMatch(p -> p.presence() == ToothPresence.PRESENT);
    assertThat(view.positions()).allMatch(p -> p.currentFindings().isEmpty());
  }
}
