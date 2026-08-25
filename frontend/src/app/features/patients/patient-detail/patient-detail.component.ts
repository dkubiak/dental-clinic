import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { AuthState } from '../../../core/auth/auth-state';
import { peselChecksumValidator } from '../pesel-validator';
import { PatientDetail } from '../patients.models';
import { PatientsService } from '../patients.service';
import { ToothChartComponent } from '../tooth-chart/tooth-chart.component';
import { VisitHistoryComponent } from '../visit-history/visit-history.component';

/**
 * Patient detail host — basic-data view/edit (FR-001/FR-011) plus the tabs later phases plug
 * into: tooth chart (US2, T050) and the visit-history placeholder (US3, T056). Reachable from
 * patient-search's result list and from patient-create's post-submit redirect.
 */
@Component({
  selector: 'app-patient-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatTabsModule,
    ToothChartComponent,
    VisitHistoryComponent,
  ],
  template: `
    @if (patient(); as p) {
      <h1>{{ p.lastName }} {{ p.firstName }}</h1>

      <mat-tab-group>
        <mat-tab label="Dane podstawowe">
          <div class="tab-content">
            @if (!editing()) {
              <dl>
                <dt>Data urodzenia</dt>
                <dd>{{ p.dateOfBirth }}</dd>
                <dt>PESEL</dt>
                <dd>{{ p.pesel ?? '—' }}</dd>
                <dt>Adres</dt>
                <dd>
                  {{ p.addressStreet }} {{ p.addressBuildingNo }}, {{ p.addressPostalCode }}
                  {{ p.addressCity }}
                </dd>
              </dl>
              <button mat-stroked-button type="button" data-testid="edit-button" (click)="startEdit()">
                Edytuj
              </button>
            } @else {
              <form [formGroup]="form" (ngSubmit)="submitEdit()">
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
                  <input matInput formControlName="pesel" />
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

                <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid">
                  Zapisz
                </button>
                <button mat-button type="button" (click)="cancelEdit()">Anuluj</button>
              </form>
            }
          </div>
        </mat-tab>
        @if (canViewToothChart()) {
          <mat-tab label="Stan uzębienia">
            <div class="tab-content">
              <app-tooth-chart [patientId]="p.id" />
            </div>
          </mat-tab>
        }
        @if (canViewVisitHistory()) {
          <mat-tab label="Historia wizyt">
            <div class="tab-content">
              <app-visit-history [patientId]="p.id" />
            </div>
          </mat-tab>
        }
      </mat-tab-group>
    }
  `,
  styles: `
    dt {
      font-weight: 600;
      margin-top: 8px;
    }
    dd {
      margin: 0;
    }
    .tab-content {
      padding: 16px 0;
    }
    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }
    .error {
      color: var(--mat-sys-error, #b3261e);
    }
  `,
})
export class PatientDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly patientsService = inject(PatientsService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly authState = inject(AuthState);

  readonly patient = signal<PatientDetail | null>(null);
  readonly editing = signal(false);
  readonly errorMessage = signal<string | null>(null);

  // FR-006 — tooth-chart access is DOCTOR/ASSISTANT only (rbac-policy.md); RECEPTION never sees
  // the tab. UX-only mirror of the backend's @PreAuthorize — the 404 it returns is the real
  // boundary (US2 Acceptance Scenario 4).
  readonly canViewToothChart = computed(() => {
    const role = this.authState.currentRole();
    return role === 'DOCTOR' || role === 'ASSISTANT';
  });

  // FR-004 — visit-history placeholder is RECEPTION/DOCTOR only (rbac-policy.md); ASSISTANT
  // never sees the tab. UX-only mirror of the backend's @PreAuthorize.
  readonly canViewVisitHistory = computed(() => {
    const role = this.authState.currentRole();
    return role === 'RECEPTION' || role === 'DOCTOR';
  });

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

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.patientsService.get(id).subscribe((patient) => this.patient.set(patient));
    }
  }

  startEdit(): void {
    const p = this.patient();
    if (!p) {
      return;
    }
    this.form.setValue({
      firstName: p.firstName,
      lastName: p.lastName,
      dateOfBirth: p.dateOfBirth,
      pesel: p.pesel ?? '',
      addressStreet: p.addressStreet,
      addressBuildingNo: p.addressBuildingNo,
      addressPostalCode: p.addressPostalCode,
      addressCity: p.addressCity,
    });
    this.errorMessage.set(null);
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  submitEdit(): void {
    const p = this.patient();
    if (!p || this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    this.patientsService
      .update(p.id, { ...raw, pesel: raw.pesel.trim() === '' ? null : raw.pesel.trim() })
      .subscribe({
        next: (updated) => {
          this.patient.set(updated);
          this.editing.set(false);
        },
        error: () => {
          this.errorMessage.set('Wystąpił błąd podczas zapisu zmian. Spróbuj ponownie.');
        },
      });
  }
}
