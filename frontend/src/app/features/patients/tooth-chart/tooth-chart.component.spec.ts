import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ToothChart, ToothPosition } from '../patients.models';
import { DiagnosisCatalogService } from './diagnosis-catalog.service';
import { ToothChartService } from './tooth-chart.service';
import { ToothChartComponent } from './tooth-chart.component';

function position(fdiNumber: number, overrides: Partial<ToothPosition> = {}): ToothPosition {
  const quadrant = Math.floor(fdiNumber / 10);
  return {
    fdiNumber,
    dentitionType: quadrant >= 5 ? 'DECIDUOUS' : 'PERMANENT',
    toothType: 'MOLAR',
    presence: 'PRESENT',
    presenceDate: null,
    version: 0,
    canals: [],
    currentFindings: [],
    ...overrides,
  };
}

const PERMANENT_FDI = [1, 2, 3, 4].flatMap((q) => [1, 2, 3, 4, 5, 6, 7, 8].map((p) => q * 10 + p));

function healthyChart(): ToothChart {
  return {
    patientId: 'p1',
    dentitionMode: 'PERMANENT',
    positions: PERMANENT_FDI.map((fdi) => position(fdi)),
  };
}

describe('ToothChartComponent', () => {
  let fixture: ComponentFixture<ToothChartComponent>;
  let toothChartService: { getChart: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    toothChartService = { getChart: vi.fn().mockReturnValue(of(healthyChart())) };
    const diagnosisCatalogService = {
      search: vi.fn().mockReturnValue(of([])),
      recentEntries: vi.fn().mockReturnValue([]),
      withRecencyTracking: vi.fn((_code: string, obs) => obs),
    };

    await TestBed.configureTestingModule({
      imports: [ToothChartComponent],
      providers: [
        { provide: ToothChartService, useValue: toothChartService },
        { provide: DiagnosisCatalogService, useValue: diagnosisCatalogService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ToothChartComponent);
    fixture.componentRef.setInput('patientId', 'p1');
  });

  it('shows a loading state before the chart resolves', () => {
    toothChartService.getChart.mockReturnValue(new Subject<ToothChart>());
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tooth-chart-loading"]')).toBeTruthy();
  });

  it('shows an error state, not an empty area, when the read fails', () => {
    toothChartService.getChart.mockReturnValue(throwError(() => new Error('boom')));
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector('[data-testid="tooth-chart-error"]');
    expect(errorEl).toBeTruthy();
    expect(errorEl.textContent.trim().length).toBeGreaterThan(0);
  });

  it('renders both arches simultaneously with all 32 permanent teeth (FR-001)', () => {
    fixture.detectChanges();

    const teethEls = fixture.nativeElement.querySelectorAll('[data-testid^="tooth-"]:not([data-testid^="tooth-chart"])');
    expect(teethEls.length).toBe(32);
    expect(fixture.nativeElement.querySelectorAll('svg.arch-upper').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('svg.arch-lower').length).toBe(1);
  });

  it('shows readable quadrant labels 1-4 for a permanent chart (FR-002)', () => {
    fixture.detectChanges();

    const labels = Array.from(fixture.nativeElement.querySelectorAll('.quadrant-label')).map(
      (el) => (el as Element).textContent,
    );
    expect(labels).toEqual(expect.arrayContaining(['1', '2', '3', '4']));
  });

  it('shows the FR-054 empty state message when the chart has no findings (US1 Scenario 1)', () => {
    fixture.detectChanges();

    const emptyEl = fixture.nativeElement.querySelector('[data-testid="tooth-chart-empty"]');
    expect(emptyEl).toBeTruthy();
    expect(emptyEl.textContent).toContain('Brak odnotowanych zmian');
    // FR-054 — the empty state must never present as a blank area: the diagram still renders.
    expect(fixture.nativeElement.querySelectorAll('[data-testid^="tooth-"]:not([data-testid^="tooth-chart"])').length).toBe(32);
  });

  it('does not show the empty-state message once a finding exists', () => {
    const chartWithFinding = healthyChart();
    chartWithFinding.positions[0] = position(11, {
      currentFindings: [
        {
          id: 'f1',
          fdiNumber: 11,
          diagnosisCatalogEntry: {
            id: 'dx1',
            code: 'K02.1',
            namePl: 'Próchnica zębiny',
            category: 'HARD_TISSUE',
            anatomicalScope: 'SURFACE',
            layer: 'DIAGNOSIS',
            icd10Code: 'K02.1',
            severityOptions: null,
            allowedForMissingTooth: false,
            deciduousAllowed: true,
            quickAccess: true,
            requiresFreeText: false,
          },
          surfaces: ['MESIAL'],
          rootCanalId: null,
          severity: null,
          freeTextDescription: null,
          note: null,
          diagnosisDate: '2026-08-30',
          resolvedDate: null,
          clinicalStatus: 'ACTIVE',
          recordStatus: 'CURRENT',
          supersedesFindingId: null,
          authorAccountId: 'a1',
          authorRole: 'DOCTOR',
          createdAt: '2026-08-30T00:00:00Z',
        },
      ],
    });
    toothChartService.getChart.mockReturnValue(of(chartWithFinding));

    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tooth-chart-empty"]')).toBeFalsy();
  });

  it('selecting a tooth updates the selection signal', () => {
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="tooth-11"]').dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedFdiNumber()).toBe(11);
  });
});
