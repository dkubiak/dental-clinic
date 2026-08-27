import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { peselChecksumValidator } from '../pesel-validator';
import { PatientsService } from '../patients.service';

/** FR-001/FR-002 — new-patient form, the shell's "Nowy pacjent" primary action target
 * (RECEPTION/DOCTOR only, enforced server-side; this form is reachable UI-wise by anyone the
 * route guard lets through — the real boundary is the 404 the backend returns to anyone else). */
@Component({
  selector: 'app-patient-create',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-card class="form-card">
      <mat-card-header>
        <mat-card-title>Nowy pacjent</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Imię</mat-label>
            <input matInput formControlName="firstName" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Nazwisko</mat-label>
            <input matInput formControlName="lastName" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Data urodzenia</mat-label>
            <input matInput type="date" formControlName="dateOfBirth" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>PESEL (opcjonalnie)</mat-label>
            <input matInput formControlName="pesel" data-testid="pesel-input" />
            @if (form.controls.pesel.errors?.['peselChecksum']) {
              <mat-error>Nieprawidłowy format lub suma kontrolna numeru PESEL.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Ulica</mat-label>
            <input matInput formControlName="addressStreet" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Numer budynku</mat-label>
            <input matInput formControlName="addressBuildingNo" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Kod pocztowy</mat-label>
            <input matInput formControlName="addressPostalCode" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Miasto</mat-label>
            <input matInput formControlName="addressCity" />
          </mat-form-field>

          @if (errorMessage()) {
            <p class="error" role="alert">{{ errorMessage() }}</p>
          }

          <button
            mat-flat-button
            color="primary"
            type="submit"
            data-testid="create-submit"
            [disabled]="form.invalid || submitting()"
          >
            Zapisz kartotekę
          </button>
        </form>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .form-card {
      max-width: 480px;
      margin: 0 auto;
    }
    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class PatientCreateComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly patientsService = inject(PatientsService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    dateOfBirth: ['', Validators.required],
    pesel: ['', peselChecksumValidator()],
    addressStreet: ['', Validators.required],
    addressBuildingNo: ['', Validators.required],
    addressPostalCode: ['', Validators.required],
    addressCity: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();
    this.patientsService
      .create({ ...raw, pesel: raw.pesel.trim() === '' ? null : raw.pesel.trim() })
      .subscribe({
        next: (created) => {
          this.submitting.set(false);
          this.router.navigate(['/patients', created.id]);
        },
        error: (error: HttpErrorResponse) => {
          this.submitting.set(false);
          this.errorMessage.set(mapCreateError(error.status));
        },
      });
  }
}

function mapCreateError(status: number): string {
  switch (status) {
    case 409:
      return 'Kartoteka z tym numerem PESEL już istnieje.';
    case 400:
      return 'Nieprawidłowy format lub suma kontrolna numeru PESEL.';
    default:
      return 'Wystąpił błąd podczas zapisu kartoteki. Spróbuj ponownie.';
  }
}
