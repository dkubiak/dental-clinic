import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthState } from '../../../core/auth/auth-state';
import { MedicalHistoryService } from '../medical-history/medical-history.service';
import { PatientsService } from '../patients.service';
import { PatientDetailComponent } from './patient-detail.component';

describe('PatientDetailComponent', () => {
  let fixture: ComponentFixture<PatientDetailComponent>;
  let patientsService: {
    get: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    getToothChart: ReturnType<typeof vi.fn>;
    getVisitHistory: ReturnType<typeof vi.fn>;
  };
  let authState: AuthState;

  const patient = {
    id: 'p1',
    firstName: 'Jan',
    lastName: 'Kowalski',
    dateOfBirth: '1990-01-15',
    pesel: '90011500013',
    addressStreet: 'Polna',
    addressBuildingNo: '12A',
    addressPostalCode: '00-001',
    addressCity: 'Warszawa',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    hasCriticalAllergyAlert: false,
  };

  beforeEach(async () => {
    patientsService = {
      get: vi.fn().mockReturnValue(of(patient)),
      update: vi.fn(),
      getToothChart: vi.fn().mockReturnValue(of([])),
      getVisitHistory: vi.fn().mockReturnValue(of([])),
    };

    await TestBed.configureTestingModule({
      imports: [PatientDetailComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: PatientsService, useValue: patientsService },
        {
          provide: MedicalHistoryService,
          useValue: { getAllergies: vi.fn().mockReturnValue(of([])) },
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'p1' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientDetailComponent);
    authState = TestBed.inject(AuthState);
  });

  it('loads and displays the patient basic data', () => {
    fixture.detectChanges();

    expect(patientsService.get).toHaveBeenCalledWith('p1');
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Kowalski');
    expect(text).toContain('Polna');
  });

  it('submits an edit via PatientsService.update', () => {
    patientsService.update.mockReturnValue(of({ ...patient, firstName: 'Janusz' }));
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.startEdit();
    component.form.controls.firstName.setValue('Janusz');
    component.submitEdit();

    expect(patientsService.update).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ firstName: 'Janusz' }),
    );
  });

  it('does not show the critical-allergy badge when hasCriticalAllergyAlert is false', () => {
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="critical-allergy-alert"]'),
    ).toBeNull();
  });

  it('shows the critical-allergy badge outside any tab, visible to RECEPTION (FR-005/SC-004)', () => {
    patientsService.get.mockReturnValue(of({ ...patient, hasCriticalAllergyAlert: true }));
    authState.setRole('RECEPTION');
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('[data-testid="critical-allergy-alert"]');
    expect(badge).toBeTruthy();
    expect(badge.querySelector('app-status-indicator')).toBeTruthy();
    // Outside any mat-tab-body — visible without opening the "Historia medyczna" tab RECEPTION
    // has no access to.
    expect(fixture.nativeElement.querySelector('mat-tab-group').contains(badge)).toBe(false);
  });

  it('does not show the "Historia medyczna" tab for RECEPTION', () => {
    authState.setRole('RECEPTION');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Historia medyczna');
  });

  it('shows the "Historia medyczna" tab for DOCTOR and ASSISTANT', () => {
    authState.setRole('DOCTOR');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Historia medyczna');
  });
});
