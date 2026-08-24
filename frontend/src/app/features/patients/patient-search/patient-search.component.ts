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
    <form class="search-form" (ngSubmit)="search()">
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
      color: rgba(0, 0, 0, 0.6);
    }
  `,
})
export class PatientSearchComponent {
  private readonly patientsService = inject(PatientsService);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly results = signal<PatientSummary[]>([]);
  readonly searched = signal(false);

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
