import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AuthState } from '../../../core/auth/auth-state';
import { StatusIndicatorComponent } from '../../../shared/status/status-indicator.component';
import { AllergyEntry, ChronicConditionEntry, MedicationEntry } from '../patients.models';
import { MedicalHistoryService } from './medical-history.service';

/**
 * "Historia medyczna" tab content (US1/US2/US3, research.md #7) — renders all three sub-resource
 * sections (alergie/leki/choroby) together on one screen (SC-001), each with a current-entries
 * list, an expandable "Historia zmian" panel, and (DOCTOR only) an add-entry form. Plugged into
 * `patient-detail.component.ts` as a fourth `mat-tab`, gated by a `canViewMedicalHistory` guard
 * there — this component itself trusts its `patientId` input and the backend's own
 * `@PreAuthorize` (UX-only convenience, not the real boundary).
 */
@Component({
  selector: 'app-medical-history',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    StatusIndicatorComponent,
  ],
  template: `
    <section class="history-section">
      <h2>Alergie</h2>

      @if (currentAllergies().length === 0) {
        <p data-testid="allergies-empty">brak odnotowanych alergii</p>
      } @else {
        <ul class="entry-list">
          @for (entry of currentAllergies(); track entry.id) {
            <li [attr.data-testid]="'allergy-' + entry.id">
              @if (entry.severity === 'CRITICAL') {
                <app-status-indicator type="error" [message]="entry.substance + ' — ' + entry.reactionType" />
              } @else {
                <span>{{ entry.substance }} — {{ entry.reactionType }} ({{ entry.severity }})</span>
              }
              @if (canEdit()) {
                <button
                  mat-button
                  type="button"
                  [attr.data-testid]="'correct-allergy-' + entry.id"
                  (click)="startCorrectingAllergy(entry)"
                >
                  Koryguj
                </button>
              }
            </li>
          }
        </ul>
      }

      <button
        mat-button
        type="button"
        data-testid="toggle-allergy-history"
        (click)="toggleAllergyHistory()"
      >
        Historia zmian
      </button>

      @if (showAllergyHistory()) {
        <ul class="entry-list history">
          @for (entry of allergyHistory(); track entry.id) {
            @if (entry.recordStatus === 'SUPERSEDED') {
              <li [attr.data-testid]="'allergy-' + entry.id" class="superseded">
                {{ entry.substance }} — {{ entry.reactionType }} ({{ entry.severity }}) — nieaktualny
              </li>
            }
          }
        </ul>
      }

      @if (canEdit()) {
        <form
          data-testid="add-allergy-form"
          [formGroup]="allergyForm"
          (ngSubmit)="submitAllergy()"
        >
          <mat-form-field appearance="outline">
            <mat-label>Substancja/czynnik</mat-label>
            <input matInput formControlName="substance" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Typ reakcji</mat-label>
            <input matInput formControlName="reactionType" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Waga</mat-label>
            <mat-select formControlName="severity">
              <mat-option value="CRITICAL">krytyczna</mat-option>
              <mat-option value="MODERATE">umiarkowana</mat-option>
            </mat-select>
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="allergyForm.invalid">
            {{ correctingAllergyId() ? 'Zapisz korektę' : 'Dodaj alergię' }}
          </button>
          @if (correctingAllergyId()) {
            <button mat-button type="button" data-testid="cancel-allergy-correction" (click)="cancelCorrectingAllergy()">
              Anuluj korektę
            </button>
          }
        </form>
      }
    </section>

    <section class="history-section">
      <h2>Przyjmowane leki</h2>

      @if (currentMedications().length === 0) {
        <p data-testid="medications-empty">brak odnotowanych leków</p>
      } @else {
        <ul class="entry-list">
          @for (entry of currentMedications(); track entry.id) {
            <li [attr.data-testid]="'medication-' + entry.id">
              {{ entry.name }} — {{ entry.dosage }} (od {{ entry.startDate }})
              @if (canEdit()) {
                <button
                  mat-button
                  type="button"
                  [attr.data-testid]="'correct-medication-' + entry.id"
                  (click)="startCorrectingMedication(entry)"
                >
                  Koryguj
                </button>
              }
            </li>
          }
        </ul>
      }

      <button
        mat-button
        type="button"
        data-testid="toggle-medication-history"
        (click)="toggleMedicationHistory()"
      >
        Historia zmian
      </button>

      @if (showMedicationHistory()) {
        <ul class="entry-list history">
          @for (entry of medicationHistory(); track entry.id) {
            @if (entry.recordStatus === 'SUPERSEDED') {
              <li [attr.data-testid]="'medication-' + entry.id" class="superseded">
                {{ entry.name }} — {{ entry.dosage }} (od {{ entry.startDate }}) — nieaktualny
              </li>
            }
          }
        </ul>
      }

      @if (canEdit()) {
        <form
          data-testid="add-medication-form"
          [formGroup]="medicationForm"
          (ngSubmit)="submitMedication()"
        >
          <mat-form-field appearance="outline">
            <mat-label>Nazwa leku</mat-label>
            <input matInput formControlName="name" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Dawka</mat-label>
            <input matInput formControlName="dosage" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Data rozpoczęcia</mat-label>
            <input matInput type="date" formControlName="startDate" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit" [disabled]="medicationForm.invalid">
            {{ correctingMedicationId() ? 'Zapisz korektę' : 'Dodaj lek' }}
          </button>
          @if (correctingMedicationId()) {
            <button mat-button type="button" data-testid="cancel-medication-correction" (click)="cancelCorrectingMedication()">
              Anuluj korektę
            </button>
          }
        </form>
      }
    </section>

    <section class="history-section">
      <h2>Choroby przewlekłe/przebyte</h2>

      @if (currentChronicConditions().length === 0) {
        <p data-testid="chronic-conditions-empty">brak odnotowanych chorób</p>
      } @else {
        <ul class="entry-list">
          @for (entry of currentChronicConditions(); track entry.id) {
            <li [attr.data-testid]="'chronic-condition-' + entry.id">
              {{ entry.name }} — {{ entry.clinicalStatus }} (rozpoznano {{ entry.diagnosisDate }})
              @if (canEdit()) {
                <button
                  mat-button
                  type="button"
                  [attr.data-testid]="'correct-chronic-condition-' + entry.id"
                  (click)="startCorrectingChronicCondition(entry)"
                >
                  Koryguj
                </button>
              }
            </li>
          }
        </ul>
      }

      <button
        mat-button
        type="button"
        data-testid="toggle-chronic-condition-history"
        (click)="toggleChronicConditionHistory()"
      >
        Historia zmian
      </button>

      @if (showChronicConditionHistory()) {
        <ul class="entry-list history">
          @for (entry of chronicConditionHistory(); track entry.id) {
            @if (entry.recordStatus === 'SUPERSEDED') {
              <li [attr.data-testid]="'chronic-condition-' + entry.id" class="superseded">
                {{ entry.name }} — {{ entry.clinicalStatus }} (rozpoznano {{ entry.diagnosisDate }})
                — nieaktualny
              </li>
            }
          }
        </ul>
      }

      @if (canEdit()) {
        <form
          data-testid="add-chronic-condition-form"
          [formGroup]="chronicConditionForm"
          (ngSubmit)="submitChronicCondition()"
        >
          <mat-form-field appearance="outline">
            <mat-label>Nazwa choroby</mat-label>
            <input matInput formControlName="name" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Status</mat-label>
            <mat-select formControlName="clinicalStatus">
              <mat-option value="ACTIVE">aktywna</mat-option>
              <mat-option value="PAST">przebyta</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Data rozpoznania</mat-label>
            <input matInput type="date" formControlName="diagnosisDate" />
          </mat-form-field>
          <button
            mat-flat-button
            color="primary"
            type="submit"
            [disabled]="chronicConditionForm.invalid"
          >
            {{ correctingChronicConditionId() ? 'Zapisz korektę' : 'Dodaj chorobę' }}
          </button>
          @if (correctingChronicConditionId()) {
            <button
              mat-button
              type="button"
              data-testid="cancel-chronic-condition-correction"
              (click)="cancelCorrectingChronicCondition()"
            >
              Anuluj korektę
            </button>
          }
        </form>
      }
    </section>
  `,
  styles: `
    .history-section {
      margin-bottom: 24px;
    }
    .entry-list {
      list-style: none;
      padding: 0;
    }
    .entry-list.history .superseded {
      opacity: 0.7;
      text-decoration: line-through;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-width: 320px;
      margin-top: 12px;
    }
  `,
})
export class MedicalHistoryComponent implements OnInit {
  readonly patientId = input.required<string>();

  private readonly medicalHistoryService = inject(MedicalHistoryService);
  private readonly authState = inject(AuthState);
  private readonly formBuilder = inject(FormBuilder);

  readonly currentAllergies = signal<AllergyEntry[]>([]);
  readonly allergyHistory = signal<AllergyEntry[]>([]);
  readonly showAllergyHistory = signal(false);
  readonly correctingAllergyId = signal<string | null>(null);

  readonly currentMedications = signal<MedicationEntry[]>([]);
  readonly medicationHistory = signal<MedicationEntry[]>([]);
  readonly showMedicationHistory = signal(false);
  readonly correctingMedicationId = signal<string | null>(null);

  readonly currentChronicConditions = signal<ChronicConditionEntry[]>([]);
  readonly chronicConditionHistory = signal<ChronicConditionEntry[]>([]);
  readonly showChronicConditionHistory = signal(false);
  readonly correctingChronicConditionId = signal<string | null>(null);

  // FR-004 — ASSISTANT has the same read scope as DOCTOR but no edit rights; RECEPTION never
  // reaches this component (patient-detail's canViewMedicalHistory guard). UX-only mirror of the
  // backend's @PreAuthorize — the 404 it returns on POST is the real boundary.
  readonly canEdit = computed(() => this.authState.currentRole() === 'DOCTOR');

  readonly allergyForm = this.formBuilder.nonNullable.group({
    substance: ['', Validators.required],
    reactionType: ['', Validators.required],
    severity: ['CRITICAL' as 'CRITICAL' | 'MODERATE', Validators.required],
  });

  readonly medicationForm = this.formBuilder.nonNullable.group({
    name: ['', Validators.required],
    dosage: ['', Validators.required],
    startDate: ['', Validators.required],
  });

  readonly chronicConditionForm = this.formBuilder.nonNullable.group({
    name: ['', Validators.required],
    clinicalStatus: ['ACTIVE' as 'ACTIVE' | 'PAST', Validators.required],
    diagnosisDate: ['', Validators.required],
  });

  ngOnInit(): void {
    this.loadCurrentAllergies();
    this.loadCurrentMedications();
    this.loadCurrentChronicConditions();
  }

  private loadCurrentAllergies(): void {
    this.medicalHistoryService
      .getAllergies(this.patientId())
      .subscribe((entries) => this.currentAllergies.set(entries));
  }

  toggleAllergyHistory(): void {
    if (this.showAllergyHistory()) {
      this.showAllergyHistory.set(false);
      return;
    }
    this.medicalHistoryService.getAllergyHistory(this.patientId()).subscribe((entries) => {
      this.allergyHistory.set(entries);
      this.showAllergyHistory.set(true);
    });
  }

  startCorrectingAllergy(entry: AllergyEntry): void {
    this.allergyForm.setValue({
      substance: entry.substance,
      reactionType: entry.reactionType,
      severity: entry.severity,
    });
    this.correctingAllergyId.set(entry.id);
  }

  cancelCorrectingAllergy(): void {
    this.correctingAllergyId.set(null);
    this.allergyForm.reset({ substance: '', reactionType: '', severity: 'CRITICAL' });
  }

  submitAllergy(): void {
    if (this.allergyForm.invalid) {
      return;
    }
    this.medicalHistoryService
      .addAllergy(this.patientId(), {
        ...this.allergyForm.getRawValue(),
        supersedesEntryId: this.correctingAllergyId(),
      })
      .subscribe(() => {
        this.allergyForm.reset({ substance: '', reactionType: '', severity: 'CRITICAL' });
        this.correctingAllergyId.set(null);
        this.loadCurrentAllergies();
      });
  }

  private loadCurrentMedications(): void {
    this.medicalHistoryService
      .getMedications(this.patientId())
      .subscribe((entries) => this.currentMedications.set(entries));
  }

  toggleMedicationHistory(): void {
    if (this.showMedicationHistory()) {
      this.showMedicationHistory.set(false);
      return;
    }
    this.medicalHistoryService.getMedicationHistory(this.patientId()).subscribe((entries) => {
      this.medicationHistory.set(entries);
      this.showMedicationHistory.set(true);
    });
  }

  startCorrectingMedication(entry: MedicationEntry): void {
    this.medicationForm.setValue({
      name: entry.name,
      dosage: entry.dosage,
      startDate: entry.startDate,
    });
    this.correctingMedicationId.set(entry.id);
  }

  cancelCorrectingMedication(): void {
    this.correctingMedicationId.set(null);
    this.medicationForm.reset({ name: '', dosage: '', startDate: '' });
  }

  submitMedication(): void {
    if (this.medicationForm.invalid) {
      return;
    }
    this.medicalHistoryService
      .addMedication(this.patientId(), {
        ...this.medicationForm.getRawValue(),
        supersedesEntryId: this.correctingMedicationId(),
      })
      .subscribe(() => {
        this.medicationForm.reset({ name: '', dosage: '', startDate: '' });
        this.correctingMedicationId.set(null);
        this.loadCurrentMedications();
      });
  }

  private loadCurrentChronicConditions(): void {
    this.medicalHistoryService
      .getChronicConditions(this.patientId())
      .subscribe((entries) => this.currentChronicConditions.set(entries));
  }

  toggleChronicConditionHistory(): void {
    if (this.showChronicConditionHistory()) {
      this.showChronicConditionHistory.set(false);
      return;
    }
    this.medicalHistoryService.getChronicConditionHistory(this.patientId()).subscribe((entries) => {
      this.chronicConditionHistory.set(entries);
      this.showChronicConditionHistory.set(true);
    });
  }

  startCorrectingChronicCondition(entry: ChronicConditionEntry): void {
    this.chronicConditionForm.setValue({
      name: entry.name,
      clinicalStatus: entry.clinicalStatus,
      diagnosisDate: entry.diagnosisDate,
    });
    this.correctingChronicConditionId.set(entry.id);
  }

  cancelCorrectingChronicCondition(): void {
    this.correctingChronicConditionId.set(null);
    this.chronicConditionForm.reset({ name: '', clinicalStatus: 'ACTIVE', diagnosisDate: '' });
  }

  submitChronicCondition(): void {
    if (this.chronicConditionForm.invalid) {
      return;
    }
    this.medicalHistoryService
      .addChronicCondition(this.patientId(), {
        ...this.chronicConditionForm.getRawValue(),
        supersedesEntryId: this.correctingChronicConditionId(),
      })
      .subscribe(() => {
        this.chronicConditionForm.reset({ name: '', clinicalStatus: 'ACTIVE', diagnosisDate: '' });
        this.correctingChronicConditionId.set(null);
        this.loadCurrentChronicConditions();
      });
  }
}
