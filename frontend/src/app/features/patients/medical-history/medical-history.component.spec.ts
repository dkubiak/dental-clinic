import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthState } from '../../../core/auth/auth-state';
import { AllergyEntry, ChronicConditionEntry, MedicationEntry } from '../patients.models';
import { MedicalHistoryService } from './medical-history.service';
import { MedicalHistoryComponent } from './medical-history.component';

describe('MedicalHistoryComponent', () => {
  let fixture: ComponentFixture<MedicalHistoryComponent>;
  let component: MedicalHistoryComponent;
  let authState: AuthState;
  let medicalHistoryService: {
    getAllergies: ReturnType<typeof vi.fn>;
    getAllergyHistory: ReturnType<typeof vi.fn>;
    addAllergy: ReturnType<typeof vi.fn>;
    getMedications: ReturnType<typeof vi.fn>;
    getMedicationHistory: ReturnType<typeof vi.fn>;
    addMedication: ReturnType<typeof vi.fn>;
    getChronicConditions: ReturnType<typeof vi.fn>;
    getChronicConditionHistory: ReturnType<typeof vi.fn>;
    addChronicCondition: ReturnType<typeof vi.fn>;
  };

  const criticalAllergy: AllergyEntry = {
    id: 'a1',
    substance: 'Penicylina',
    reactionType: 'Anafilaksja',
    severity: 'CRITICAL',
    recordStatus: 'CURRENT',
    supersedesEntryId: null,
    createdAt: '2026-01-01T00:00:00Z',
  };

  const supersededAllergy: AllergyEntry = {
    ...criticalAllergy,
    id: 'a0',
    severity: 'MODERATE',
    recordStatus: 'SUPERSEDED',
  };

  function createComponent() {
    fixture = TestBed.createComponent(MedicalHistoryComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('patientId', 'p1');
    fixture.detectChanges();
  }

  beforeEach(async () => {
    medicalHistoryService = {
      getAllergies: vi.fn().mockReturnValue(of([])),
      getAllergyHistory: vi.fn().mockReturnValue(of([])),
      addAllergy: vi.fn(),
      getMedications: vi.fn().mockReturnValue(of([])),
      getMedicationHistory: vi.fn().mockReturnValue(of([])),
      addMedication: vi.fn(),
      getChronicConditions: vi.fn().mockReturnValue(of([])),
      getChronicConditionHistory: vi.fn().mockReturnValue(of([])),
      addChronicCondition: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [MedicalHistoryComponent],
      providers: [
        provideAnimationsAsync(),
        { provide: MedicalHistoryService, useValue: medicalHistoryService },
      ],
    }).compileComponents();

    authState = TestBed.inject(AuthState);
  });

  it('shows the empty state when there are no allergy entries', () => {
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('brak odnotowanych alergii');
  });

  it('renders a CRITICAL entry via app-status-indicator', () => {
    medicalHistoryService.getAllergies.mockReturnValue(of([criticalAllergy]));
    createComponent();

    const indicator = fixture.nativeElement.querySelector(
      '[data-testid="allergy-a1"] app-status-indicator',
    );
    expect(indicator).toBeTruthy();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Penicylina');
  });

  it('shows the add-entry form only for DOCTOR', () => {
    authState.setRole('ASSISTANT');
    createComponent();
    expect(fixture.nativeElement.querySelector('[data-testid="add-allergy-form"]')).toBeNull();

    authState.setRole('DOCTOR');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="add-allergy-form"]')).toBeTruthy();
  });

  it('toggling "Historia zmian" loads and shows superseded entries', () => {
    medicalHistoryService.getAllergies.mockReturnValue(of([criticalAllergy]));
    medicalHistoryService.getAllergyHistory.mockReturnValue(
      of([criticalAllergy, supersededAllergy]),
    );
    createComponent();

    expect(fixture.nativeElement.querySelector('[data-testid="allergy-a0"]')).toBeNull();

    fixture.nativeElement
      .querySelector('[data-testid="toggle-allergy-history"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(medicalHistoryService.getAllergyHistory).toHaveBeenCalledWith('p1');
    expect(fixture.nativeElement.querySelector('[data-testid="allergy-a0"]')).toBeTruthy();
  });

  it('submitting the add-allergy form calls MedicalHistoryService.addAllergy and refreshes the list', () => {
    authState.setRole('DOCTOR');
    medicalHistoryService.addAllergy.mockReturnValue(of(criticalAllergy));
    createComponent();

    component.allergyForm.setValue({
      substance: 'Penicylina',
      reactionType: 'Anafilaksja',
      severity: 'CRITICAL',
    });
    component.submitAllergy();

    expect(medicalHistoryService.addAllergy).toHaveBeenCalledWith('p1', {
      substance: 'Penicylina',
      reactionType: 'Anafilaksja',
      severity: 'CRITICAL',
      supersedesEntryId: null,
    });
  });

  it('clicking "Koryguj" pre-fills the form and submits with supersedesEntryId set (FR-010)', () => {
    authState.setRole('DOCTOR');
    medicalHistoryService.getAllergies.mockReturnValue(of([criticalAllergy]));
    medicalHistoryService.addAllergy.mockReturnValue(of({ ...criticalAllergy, id: 'a2' }));
    createComponent();

    fixture.nativeElement.querySelector('[data-testid="correct-allergy-a1"]').click();
    fixture.detectChanges();

    expect(component.allergyForm.getRawValue()).toEqual({
      substance: 'Penicylina',
      reactionType: 'Anafilaksja',
      severity: 'CRITICAL',
    });

    component.allergyForm.controls.severity.setValue('MODERATE');
    component.submitAllergy();

    expect(medicalHistoryService.addAllergy).toHaveBeenCalledWith('p1', {
      substance: 'Penicylina',
      reactionType: 'Anafilaksja',
      severity: 'MODERATE',
      supersedesEntryId: 'a1',
    });
  });

  const medication: MedicationEntry = {
    id: 'm1',
    name: 'Ibuprofen',
    dosage: '400mg',
    startDate: '2026-01-01',
    recordStatus: 'CURRENT',
    supersedesEntryId: null,
    createdAt: '2026-01-01T00:00:00Z',
  };

  it('shows the empty state when there are no medication entries', () => {
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('brak odnotowanych leków');
  });

  it('renders a medication entry with its start date', () => {
    medicalHistoryService.getMedications.mockReturnValue(of([medication]));
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Ibuprofen');
    expect(text).toContain('2026-01-01');
  });

  it('shows the add-medication form only for DOCTOR', () => {
    authState.setRole('ASSISTANT');
    createComponent();
    expect(fixture.nativeElement.querySelector('[data-testid="add-medication-form"]')).toBeNull();

    authState.setRole('DOCTOR');
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="add-medication-form"]'),
    ).toBeTruthy();
  });

  it('submitting the add-medication form calls MedicalHistoryService.addMedication', () => {
    authState.setRole('DOCTOR');
    medicalHistoryService.addMedication.mockReturnValue(of(medication));
    createComponent();

    component.medicationForm.setValue({
      name: 'Ibuprofen',
      dosage: '400mg',
      startDate: '2026-01-01',
    });
    component.submitMedication();

    expect(medicalHistoryService.addMedication).toHaveBeenCalledWith('p1', {
      name: 'Ibuprofen',
      dosage: '400mg',
      startDate: '2026-01-01',
      supersedesEntryId: null,
    });
  });

  it('clicking "Koryguj" on a medication pre-fills the form and submits with supersedesEntryId set', () => {
    authState.setRole('DOCTOR');
    medicalHistoryService.getMedications.mockReturnValue(of([medication]));
    medicalHistoryService.addMedication.mockReturnValue(of({ ...medication, id: 'm2' }));
    createComponent();

    fixture.nativeElement.querySelector('[data-testid="correct-medication-m1"]').click();
    fixture.detectChanges();
    component.medicationForm.controls.dosage.setValue('200mg');
    component.submitMedication();

    expect(medicalHistoryService.addMedication).toHaveBeenCalledWith('p1', {
      name: 'Ibuprofen',
      dosage: '200mg',
      startDate: '2026-01-01',
      supersedesEntryId: 'm1',
    });
  });

  const chronicCondition: ChronicConditionEntry = {
    id: 'c1',
    name: 'Cukrzyca typu 2',
    clinicalStatus: 'ACTIVE',
    diagnosisDate: '2020-03-15',
    recordStatus: 'CURRENT',
    supersedesEntryId: null,
    createdAt: '2026-01-01T00:00:00Z',
  };

  it('shows the empty state when there are no chronic-condition entries', () => {
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('brak odnotowanych chorób');
  });

  it('renders a chronic-condition entry with its clinical status and diagnosis date', () => {
    medicalHistoryService.getChronicConditions.mockReturnValue(of([chronicCondition]));
    createComponent();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Cukrzyca typu 2');
    expect(text).toContain('2020-03-15');
    expect(text).toContain('ACTIVE');
  });

  it('shows the add-chronic-condition form only for DOCTOR', () => {
    authState.setRole('ASSISTANT');
    createComponent();
    expect(
      fixture.nativeElement.querySelector('[data-testid="add-chronic-condition-form"]'),
    ).toBeNull();

    authState.setRole('DOCTOR');
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('[data-testid="add-chronic-condition-form"]'),
    ).toBeTruthy();
  });

  it('submitting the add-chronic-condition form calls MedicalHistoryService.addChronicCondition', () => {
    authState.setRole('DOCTOR');
    medicalHistoryService.addChronicCondition.mockReturnValue(of(chronicCondition));
    createComponent();

    component.chronicConditionForm.setValue({
      name: 'Cukrzyca typu 2',
      clinicalStatus: 'ACTIVE',
      diagnosisDate: '2020-03-15',
    });
    component.submitChronicCondition();

    expect(medicalHistoryService.addChronicCondition).toHaveBeenCalledWith('p1', {
      name: 'Cukrzyca typu 2',
      clinicalStatus: 'ACTIVE',
      diagnosisDate: '2020-03-15',
      supersedesEntryId: null,
    });
  });

  it('clicking "Koryguj" on a chronic condition can flip clinicalStatus independently of the correction (Clarifications Q1)', () => {
    authState.setRole('DOCTOR');
    medicalHistoryService.getChronicConditions.mockReturnValue(of([chronicCondition]));
    medicalHistoryService.addChronicCondition.mockReturnValue(of({ ...chronicCondition, id: 'c2' }));
    createComponent();

    fixture.nativeElement.querySelector('[data-testid="correct-chronic-condition-c1"]').click();
    fixture.detectChanges();
    component.chronicConditionForm.controls.clinicalStatus.setValue('PAST');
    component.submitChronicCondition();

    expect(medicalHistoryService.addChronicCondition).toHaveBeenCalledWith('p1', {
      name: 'Cukrzyca typu 2',
      clinicalStatus: 'PAST',
      diagnosisDate: '2020-03-15',
      supersedesEntryId: 'c1',
    });
  });
});
