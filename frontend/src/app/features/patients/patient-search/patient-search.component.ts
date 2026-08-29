import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { PatientsService } from '../patients.service';
import { PatientSummary } from '../patients.models';

/** FR-012 — find an existing patient by last-name fragment or exact PESEL. Default landing screen
 * for RECEPTION/DOCTOR/ASSISTANT (app.routes.ts). */
@Component({
  selector: 'app-patient-search',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatListModule,
  ],
  template: `
    <h1>Pacjenci</h1>
    <form class="search-form" (submit)="onSubmit($event)">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Nazwisko lub PESEL</mat-label>
        <input matInput [formControl]="searchControl" data-testid="search-input" />
      </mat-form-field>
      <button mat-flat-button color="primary" type="submit" data-testid="search-submit">
        Szukaj
      </button>
    </form>

    @if (searched()) {
      @if (results().length > 0) {
        <mat-nav-list>
          @for (patient of results(); track patient.id) {
            <a mat-list-item [routerLink]="['/patients', patient.id]">
              {{ patient.lastName }} {{ patient.firstName }}
              @if (patient.pesel) {
                <span class="pesel"> — {{ patient.pesel }}</span>
              }
            </a>
          }
        </mat-nav-list>
      } @else {
        <p>Brak wyników dla podanego zapytania.</p>
      }
    }
  `,
  styles: `
    .search-form {
      display: flex;
      gap: 8px;
      align-items: flex-start;
      flex-wrap: wrap;
    }
    .full-width {
      flex: 1 1 240px;
    }
    .pesel {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class PatientSearchComponent {
  private readonly patientsService = inject(PatientsService);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly results = signal<PatientSummary[]>([]);
  readonly searched = signal(false);

  // Plain (submit), not (ngSubmit): only ReactiveFormsModule is imported here (no [formGroup]
  // on this bare-FormControl search box), and (ngSubmit) is an NgForm/FormGroupDirective output
  // — neither is attached to this <form>, so (ngSubmit) never actually bound to anything and the
  // click fell through to the browser's native, un-prevented GET form submission (discovered as
  // a live gap while running 002-patient-records' quickstart validation, T063 — no prior test
  // exercised the real DOM submit event, only component.search() directly).
  onSubmit(event: Event): void {
    event.preventDefault();
    this.search();
  }

  search(): void {
    const q = this.searchControl.value.trim();
    if (q === '') {
      return;
    }
    this.patientsService.search(q).subscribe((results) => {
      this.results.set(results);
      this.searched.set(true);
    });
  }
}
