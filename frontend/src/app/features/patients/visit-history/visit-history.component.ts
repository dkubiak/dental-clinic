import { Component, Input, OnInit, inject } from '@angular/core';
import { PatientsService } from '../patients.service';

/** US3 — read-only visit-history placeholder (FR-004). Always renders the empty state: no visit
 * data exists yet, and no add-entry control exists anywhere here (superseded by a future,
 * separately specified visits module, spec.md). Used as a tab inside patient-detail (T056). */
@Component({
  selector: 'app-visit-history',
  standalone: true,
  imports: [],
  template: `<p>Historia wizyt pojawi się tutaj po wdrożeniu modułu wizyt.</p>`,
})
export class VisitHistoryComponent implements OnInit {
  @Input({ required: true }) patientId!: string;

  private readonly patientsService = inject(PatientsService);

  ngOnInit(): void {
    this.patientsService.getVisitHistory(this.patientId).subscribe();
  }
}
