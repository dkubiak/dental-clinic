import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PatientsService } from '../patients.service';
import { PatientDetailComponent } from './patient-detail.component';

describe('PatientDetailComponent', () => {
  let fixture: ComponentFixture<PatientDetailComponent>;
  let patientsService: { get: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn> };

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
  };

  beforeEach(async () => {
    patientsService = { get: vi.fn().mockReturnValue(of(patient)), update: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [PatientDetailComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: PatientsService, useValue: patientsService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => 'p1' } } },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientDetailComponent);
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
});
