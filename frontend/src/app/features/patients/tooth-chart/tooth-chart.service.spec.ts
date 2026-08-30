import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ToothChart, ToothFinding } from '../patients.models';
import { ToothChartService } from './tooth-chart.service';

describe('ToothChartService', () => {
  let service: ToothChartService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ToothChartService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getChart requests GET /patients/{id}/tooth-chart', () => {
    const expected: ToothChart = { patientId: 'p1', dentitionMode: 'PERMANENT', positions: [] };

    let actual: ToothChart | undefined;
    service.getChart('p1').subscribe((chart) => (actual = chart));

    const req = httpMock.expectOne('/patients/p1/tooth-chart');
    expect(req.request.method).toBe('GET');
    req.flush(expected);

    expect(actual).toEqual(expected);
  });

  it('getPositionHistory requests GET .../positions/{fdi}/history', () => {
    const expected: ToothFinding[] = [];

    service.getPositionHistory('p1', 11).subscribe();

    const req = httpMock.expectOne('/patients/p1/tooth-chart/positions/11/history');
    expect(req.request.method).toBe('GET');
    req.flush(expected);
  });

  it('addFinding posts to /patients/{id}/tooth-chart/findings', () => {
    service
      .addFinding('p1', {
        fdiNumber: 36,
        diagnosisCatalogEntryId: 'dx1',
        surfaces: ['MESIAL'],
        diagnosisDate: '2026-08-30',
      })
      .subscribe();

    const req = httpMock.expectOne('/patients/p1/tooth-chart/findings');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      fdiNumber: 36,
      diagnosisCatalogEntryId: 'dx1',
      surfaces: ['MESIAL'],
      diagnosisDate: '2026-08-30',
    });
    req.flush({});
  });

  it('searchDiagnosisCatalog passes q and quickAccessOnly as query params', () => {
    service.searchDiagnosisCatalog('próchnica', true).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === '/diagnosis-catalog' && r.params.get('q') === 'próchnica' && r.params.get('quickAccessOnly') === 'true',
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('searchDiagnosisCatalog omits params when not provided', () => {
    service.searchDiagnosisCatalog().subscribe();

    const req = httpMock.expectOne('/diagnosis-catalog');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('quickAccessOnly')).toBe(false);
    req.flush([]);
  });

  it('closeFinding posts to .../findings/{id}/close', () => {
    service.closeFinding('p1', 'f1', { resolvedDate: '2026-08-30' }).subscribe();

    const req = httpMock.expectOne('/patients/p1/tooth-chart/findings/f1/close');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('changePresence patches .../positions/{fdi}/presence with expectedVersion', () => {
    service.changePresence('p1', 11, { presence: 'EXTRACTED', expectedVersion: 0 }).subscribe();

    const req = httpMock.expectOne('/patients/p1/tooth-chart/positions/11/presence');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.expectedVersion).toBe(0);
    req.flush({});
  });
});
