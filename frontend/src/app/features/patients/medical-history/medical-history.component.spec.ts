import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthState } from '../../../core/auth/auth-state';
import { AllergyEntry } from '../patients.models';
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
    });
  });
});
