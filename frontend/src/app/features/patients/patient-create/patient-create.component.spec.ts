import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PatientsService } from '../patients.service';
import { PatientCreateComponent } from './patient-create.component';

describe('PatientCreateComponent', () => {
  let fixture: ComponentFixture<PatientCreateComponent>;
  let component: PatientCreateComponent;
  let patientsService: { create: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    patientsService = { create: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [PatientCreateComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: PatientsService, useValue: patientsService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientCreateComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  function fillRequiredFields(): void {
    component.form.setValue({
      firstName: 'Jan',
      lastName: 'Kowalski',
      dateOfBirth: '1990-01-15',
      pesel: '',
      addressStreet: 'Polna',
      addressBuildingNo: '12A',
      addressPostalCode: '00-001',
      addressCity: 'Warszawa',
    });
  }

  it('keeps submit disabled until required fields are filled', () => {
    expect(component.form.invalid).toBe(true);
    fillRequiredFields();
    expect(component.form.valid).toBe(true);
  });

  it('rejects a PESEL with an invalid checksum client-side (UX only, FR-002)', () => {
    fillRequiredFields();
    component.form.controls.pesel.setValue('90011500021'); // altered last digit
    expect(component.form.invalid).toBe(true);
  });

  it('accepts a PESEL with a valid checksum', () => {
    fillRequiredFields();
    component.form.controls.pesel.setValue('90011500013');
    expect(component.form.valid).toBe(true);
  });

  it('submits and navigates to the new patient on success', () => {
    fillRequiredFields();
    patientsService.create.mockReturnValue(of({ id: 'new-id' }));

    component.submit();

    expect(patientsService.create).toHaveBeenCalledWith(
      expect.objectContaining({ firstName: 'Jan', lastName: 'Kowalski', pesel: null }),
    );
    expect(router.navigate).toHaveBeenCalledWith(['/patients', 'new-id']);
  });

  it('shows an error message on 409 duplicate PESEL', () => {
    fillRequiredFields();
    patientsService.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );

    component.submit();
    fixture.detectChanges();

    expect(component.errorMessage()).toContain('PESEL');
  });
});
