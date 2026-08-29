import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AllergyEntry, ChronicConditionEntry, MedicationEntry } from '../patients.models';
import { MedicalHistoryService } from './medical-history.service';

describe('MedicalHistoryService', () => {
  let service: MedicalHistoryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MedicalHistoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  const allergy: AllergyEntry = {
    id: 'a1',
    substance: 'Penicylina',
    reactionType: 'Anafilaksja',
    severity: 'CRITICAL',
    recordStatus: 'CURRENT',
    supersedesEntryId: null,
    createdAt: '2026-01-01T00:00:00Z',
  };

  it('getAllergies calls GET /patients/:id/allergies', () => {
    service.getAllergies('p1').subscribe((result) => {
      expect(result).toEqual([allergy]);
    });

    const req = httpMock.expectOne('/patients/p1/allergies');
    expect(req.request.method).toBe('GET');
    req.flush([allergy]);
  });

  it('getAllergyHistory calls GET /patients/:id/allergies/history', () => {
    service.getAllergyHistory('p1').subscribe((result) => {
      expect(result).toEqual([allergy]);
    });

    const req = httpMock.expectOne('/patients/p1/allergies/history');
    expect(req.request.method).toBe('GET');
    req.flush([allergy]);
  });

  it('addAllergy calls POST /patients/:id/allergies with the request body', () => {
    service
      .addAllergy('p1', { substance: 'Penicylina', reactionType: 'Anafilaksja', severity: 'CRITICAL' })
      .subscribe((result) => {
        expect(result).toEqual(allergy);
      });

    const req = httpMock.expectOne('/patients/p1/allergies');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      substance: 'Penicylina',
      reactionType: 'Anafilaksja',
      severity: 'CRITICAL',
    });
    req.flush(allergy);
  });

  const medication: MedicationEntry = {
    id: 'm1',
    name: 'Ibuprofen',
    dosage: '400mg 2x/dzień',
    startDate: '2026-01-01',
    recordStatus: 'CURRENT',
    supersedesEntryId: null,
    createdAt: '2026-01-01T00:00:00Z',
  };

  it('getMedications calls GET /patients/:id/medications', () => {
    service.getMedications('p1').subscribe((result) => {
      expect(result).toEqual([medication]);
    });

    const req = httpMock.expectOne('/patients/p1/medications');
    expect(req.request.method).toBe('GET');
    req.flush([medication]);
  });

  it('getMedicationHistory calls GET /patients/:id/medications/history', () => {
    service.getMedicationHistory('p1').subscribe((result) => {
      expect(result).toEqual([medication]);
    });

    const req = httpMock.expectOne('/patients/p1/medications/history');
    expect(req.request.method).toBe('GET');
    req.flush([medication]);
  });

  it('addMedication calls POST /patients/:id/medications with the request body', () => {
    service
      .addMedication('p1', { name: 'Ibuprofen', dosage: '400mg 2x/dzień', startDate: '2026-01-01' })
      .subscribe((result) => {
        expect(result).toEqual(medication);
      });

    const req = httpMock.expectOne('/patients/p1/medications');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'Ibuprofen',
      dosage: '400mg 2x/dzień',
      startDate: '2026-01-01',
    });
    req.flush(medication);
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

  it('getChronicConditions calls GET /patients/:id/chronic-conditions', () => {
    service.getChronicConditions('p1').subscribe((result) => {
      expect(result).toEqual([chronicCondition]);
    });

    const req = httpMock.expectOne('/patients/p1/chronic-conditions');
    expect(req.request.method).toBe('GET');
    req.flush([chronicCondition]);
  });

  it('getChronicConditionHistory calls GET /patients/:id/chronic-conditions/history', () => {
    service.getChronicConditionHistory('p1').subscribe((result) => {
      expect(result).toEqual([chronicCondition]);
    });

    const req = httpMock.expectOne('/patients/p1/chronic-conditions/history');
    expect(req.request.method).toBe('GET');
    req.flush([chronicCondition]);
  });

  it('addChronicCondition calls POST /patients/:id/chronic-conditions with the request body', () => {
    service
      .addChronicCondition('p1', {
        name: 'Cukrzyca typu 2',
        clinicalStatus: 'ACTIVE',
        diagnosisDate: '2020-03-15',
      })
      .subscribe((result) => {
        expect(result).toEqual(chronicCondition);
      });

    const req = httpMock.expectOne('/patients/p1/chronic-conditions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'Cukrzyca typu 2',
      clinicalStatus: 'ACTIVE',
      diagnosisDate: '2020-03-15',
    });
    req.flush(chronicCondition);
  });
});
