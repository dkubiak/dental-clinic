import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AllergyEntry } from '../patients.models';
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
});
