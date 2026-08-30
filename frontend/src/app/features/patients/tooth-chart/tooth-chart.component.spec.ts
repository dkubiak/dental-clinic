import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
const DECIDUOUS_FDI = [5, 6, 7, 8].flatMap((q) => [1, 2, 3, 4, 5].map((p) => q * 10 + p));

function healthyChart(): ToothChart {
  return {
    patientId: 'p1',
    dentitionMode: 'PERMANENT',
    positions: PERMANENT_FDI.map((fdi) => position(fdi)),
  };
}

function deciduousChart(): ToothChart {
  return {
    patientId: 'p1',
    dentitionMode: 'DECIDUOUS',
    positions: [...PERMANENT_FDI.map((fdi) => position(fdi)), ...DECIDUOUS_FDI.map((fdi) => position(fdi))],
  };
}

function mixedChart(): ToothChart {
  return { ...deciduousChart(), dentitionMode: 'MIXED' };
}

describe('ToothChartComponent', () => {
  let fixture: ComponentFixture<ToothChartComponent>;
  let toothChartService: {
    getChart: ReturnType<typeof vi.fn>;
    changeDentitionMode: ReturnType<typeof vi.fn>;
    conflict$: Subject<string>;
  };

  beforeEach(async () => {
    toothChartService = {
      getChart: vi.fn().mockReturnValue(of(healthyChart())),
      changeDentitionMode: vi.fn().mockReturnValue(of(healthyChart())),
      conflict$: new Subject<string>(),
    };
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

  afterEach(() => {
    delete (Element.prototype as { scrollIntoView?: unknown }).scrollIntoView;
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

  it('shows a Polish legend explaining every state/layer/surface symbol (FR-008)', () => {
    fixture.detectChanges();

    const legend = fixture.nativeElement.querySelector('[data-testid="tooth-chart-legend"]');
    expect(legend).toBeTruthy();
    const text = legend.textContent as string;
    // stan pozycji
    expect(text).toContain('Ząb zdrowy');
    expect(text).toContain('Brak zęba');
    expect(text).toContain('Niewyrznięty');
    // warstwa wpisu
    expect(text).toContain('Rozpoznanie');
    expect(text).toContain('Stan istniejący');
    // symbolika powierzchni
    expect(text).toContain('mezjalna');
    expect(text).toContain('dystalna');
    expect(text).toContain('przedsionkowa');
    expect(text).toContain('językowa');
    expect(text).toContain('sieczna');
  });

  it('the layer filter dims EXISTING_STATE markers while keeping DIAGNOSIS markers visible, purely client-side (FR-009)', () => {
    const chart = healthyChart();
    chart.positions[0] = position(11, {
      currentFindings: [
        {
          id: 'diag1',
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
    chart.positions[1] = position(12, {
      currentFindings: [
        {
          id: 'exist1',
          fdiNumber: 12,
          diagnosisCatalogEntry: {
            id: 'dx2',
            code: 'REST01',
            namePl: 'Wypełnienie (istniejące)',
            category: 'POST_TREATMENT_RESTORATION',
            anatomicalScope: 'SURFACE',
            layer: 'EXISTING_STATE',
            icd10Code: null,
            severityOptions: null,
            allowedForMissingTooth: false,
            deciduousAllowed: true,
            quickAccess: true,
            requiresFreeText: false,
          },
          surfaces: ['DISTAL'],
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
    toothChartService.getChart.mockReturnValue(of(chart));
    fixture.detectChanges();

    const tooth11 = fixture.nativeElement.querySelector('[data-testid="tooth-11"]');
    const tooth12 = fixture.nativeElement.querySelector('[data-testid="tooth-12"]');
    expect(tooth11.classList.contains('layer-dimmed')).toBe(false);
    expect(tooth12.classList.contains('layer-dimmed')).toBe(false);

    fixture.nativeElement
      .querySelector('[data-testid="layer-filter-diagnosis"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    // filtering is purely a view concern — the underlying chart data is untouched
    expect(fixture.componentInstance.chart()).toBe(chart);
    expect(tooth11.classList.contains('layer-dimmed')).toBe(false);
    expect(tooth11.classList.contains('status-diseased')).toBe(true);
    expect(tooth12.classList.contains('layer-dimmed')).toBe(true);
    // FR-039/FR-050 — the non-color state class must still be present while dimmed
    expect(tooth12.classList.contains('status-restored')).toBe(true);
  });

  it('renders one surface-map instance per visible tooth column in a middle strip between the two arches (FR-029)', () => {
    fixture.detectChanges();

    const strip = fixture.nativeElement.querySelector('[data-testid="surface-strip"]');
    expect(strip).toBeTruthy();
    expect(fixture.nativeElement.querySelectorAll('app-surface-map').length).toBe(32);

    const layoutChildren = Array.from(
      fixture.nativeElement.querySelector('.odontogram').children,
    ) as Element[];
    const archIndices = layoutChildren
      .map((el, i) => (el.classList.contains('arch-wrapper') ? i : -1))
      .filter((i) => i >= 0);
    const stripIndex = layoutChildren.indexOf(strip);
    expect(archIndices.length).toBe(2);
    expect(archIndices[0]).toBeLessThan(stripIndex);
    expect(stripIndex).toBeLessThan(archIndices[1]);
  });

  it('clicking a surface zone in the middle strip selects the matching tooth+surface directly, without opening the panel first (FR-029a)', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-tooth-detail-panel')).toBeFalsy();
    expect(fixture.componentInstance.selectedFdiNumber()).toBeNull();

    fixture.nativeElement
      .querySelector('[data-testid="surface-cell-11"] [data-testid="surface-zone-MESIAL"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedFdiNumber()).toBe(11);
    expect(fixture.nativeElement.querySelector('app-tooth-detail-panel')).toBeTruthy();
    expect(fixture.componentInstance.presetSurface()).toEqual({ fdiNumber: 11, surface: 'MESIAL' });
  });

  it('right-clicking a surface zone in the middle strip opens the quick context-menu pre-targeted at that surface (FR-020a/T066)', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="tooth-context-menu"]')).toBeFalsy();

    fixture.nativeElement
      .querySelector('[data-testid="surface-cell-11"] [data-testid="surface-zone-DISTAL"]')
      .dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true, clientX: 40, clientY: 50 }));
    fixture.detectChanges();

    expect(fixture.componentInstance.contextMenu()).toEqual({ fdiNumber: 11, x: 40, y: 50, surface: 'DISTAL' });
    expect(fixture.nativeElement.querySelector('[data-testid="tooth-context-menu"]')).toBeTruthy();
  });

  it('the diagram scrolls horizontally only inside its own container, never as page scroll (FR-049)', () => {
    fixture.detectChanges();

    const container = fixture.nativeElement.querySelector('[data-testid="diagram-scroll-container"]');
    expect(container).toBeTruthy();
    expect(container.style.overflowX).toBe('auto');
  });

  it('a zoom control reaches >=24x24px zones at its first level and >=44x44px at its highest, keeping the selected tooth in view (FR-029b/FR-049)', () => {
    const scrollIntoViewSpy = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoViewSpy;
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="tooth-11"]').dispatchEvent(new Event('click'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="zoom-2"]').dispatchEvent(new Event('click'));
    fixture.detectChanges();
    expect(minZoneDimension('surface-cell-11')).toBeGreaterThanOrEqual(24);
    expect(scrollIntoViewSpy).toHaveBeenCalled();

    scrollIntoViewSpy.mockClear();
    fixture.nativeElement.querySelector('[data-testid="zoom-3"]').dispatchEvent(new Event('click'));
    fixture.detectChanges();
    expect(minZoneDimension('surface-cell-11')).toBeGreaterThanOrEqual(44);
    expect(scrollIntoViewSpy).toHaveBeenCalled();

    function minZoneDimension(cellTestId: string): number {
      const zone = fixture.nativeElement.querySelector(
        `[data-testid="${cellTestId}"] [data-testid="surface-zone-MESIAL"] path`,
      );
      const d = zone.getAttribute('d') as string;
      const numbers = (d.match(/-?\d+(\.\d+)?/g) ?? []).map(Number);
      const xs = numbers.filter((_, i) => i % 2 === 0);
      const ys = numbers.filter((_, i) => i % 2 === 1);
      const width = Math.max(...xs) - Math.min(...xs);
      const height = Math.max(...ys) - Math.min(...ys);
      return Math.min(width, height);
    }
  });

  it('a 409 conflict from any write method surfaces a Polish message with a przeładuj action, never a silent failure (FR-070/SC-010)', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="conflict-banner"]')).toBeFalsy();

    toothChartService.conflict$.next(
      'Ktoś inny zmienił ten wpis w międzyczasie. Przeładuj dane, aby zobaczyć aktualny stan.',
    );
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('[data-testid="conflict-banner"]');
    expect(banner).toBeTruthy();
    expect(banner.textContent).toContain('Przeładuj');

    toothChartService.getChart.mockClear();
    fixture.nativeElement
      .querySelector('[data-testid="conflict-reload"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(toothChartService.getChart).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-testid="conflict-banner"]')).toBeFalsy();
  });

  it('renders the 20 deciduous positions by default for a child patient, distinguished by more than numbering (T106/FR-046)', () => {
    toothChartService.getChart.mockReturnValue(of(deciduousChart()));
    fixture.detectChanges();

    const teethEls = fixture.nativeElement.querySelectorAll(
      '[data-testid^="tooth-"]:not([data-testid^="tooth-chart"])',
    );
    expect(teethEls.length).toBe(20);
    expect(fixture.nativeElement.querySelector('[data-testid="dentition-mode-DECIDUOUS"]').getAttribute('aria-pressed')).toBe('true');
    // FR-046 — visually distinguished by more than the FDI numbering: a non-permanent CSS class.
    expect(
      Array.from(teethEls as unknown as Element[]).every((el) => el.classList.contains('tooth-deciduous')),
    ).toBe(true);
  });

  it('renders both deciduous and permanent positions simultaneously in mixed mode (T106/FR-046)', () => {
    toothChartService.getChart.mockReturnValue(of(mixedChart()));
    fixture.detectChanges();

    const teethEls = fixture.nativeElement.querySelectorAll(
      '[data-testid^="tooth-"]:not([data-testid^="tooth-chart"])',
    );
    expect(teethEls.length).toBe(52);
    const deciduousCount = Array.from(teethEls as unknown as Element[]).filter((el) =>
      el.classList.contains('tooth-deciduous'),
    ).length;
    expect(deciduousCount).toBe(20);
  });

  it('the dentition-mode switcher calls changeDentitionMode and applies the returned chart', () => {
    const switched = { ...healthyChart(), dentitionMode: 'MIXED' as const };
    toothChartService.changeDentitionMode.mockReturnValue(of(switched));
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="dentition-mode-MIXED"]')
      .dispatchEvent(new Event('click'));

    expect(toothChartService.changeDentitionMode).toHaveBeenCalledWith('p1', { dentitionMode: 'MIXED' });
    expect(fixture.componentInstance.chart()).toBe(switched);
  });

  describe('multi-selection (T114/FR-004a-c)', () => {
    beforeEach(() => {
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="multi-select-toggle"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();
    });

    it('clicking teeth toggles them in/out of the selection, with a visible counter', () => {
      fixture.nativeElement.querySelector('[data-testid="tooth-11"]').dispatchEvent(new Event('click'));
      fixture.nativeElement.querySelector('[data-testid="tooth-12"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();

      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([11, 12]);
      expect(fixture.nativeElement.querySelector('[data-testid="multi-select-count"]').textContent).toContain('2');

      // deselecting one tooth leaves the rest intact
      fixture.nativeElement.querySelector('[data-testid="tooth-11"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();
      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([12]);
    });

    it('a quadrant shortcut selects every visible tooth in that quadrant', () => {
      fixture.nativeElement.querySelector('[data-testid="select-quadrant-2"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();

      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([21, 22, 23, 24, 25, 26, 27, 28]);
    });

    it('an arch shortcut selects every visible tooth in that arch', () => {
      fixture.nativeElement.querySelector('[data-testid="select-arch-upper"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();

      expect(fixture.componentInstance.selectedFdiNumbers().length).toBe(16);
      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual(
        expect.arrayContaining([11, 21]),
      );
    });

    it('the anterior-segment shortcut selects only incisors/canines across every quadrant', () => {
      fixture.nativeElement.querySelector('[data-testid="select-anterior-segment"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();

      const selected = fixture.componentInstance.selectedFdiNumbers();
      expect(selected.every((fdi) => fdi % 10 <= 3)).toBe(true);
      expect(selected.length).toBe(12); // 3 anterior positions x 4 quadrants
    });

    it('drag-select across adjacent teeth adds each one, including the tooth the drag started on', () => {
      const start = fixture.nativeElement.querySelector('[data-testid="tooth-11"]');
      const mid = fixture.nativeElement.querySelector('[data-testid="tooth-12"]');
      const end = fixture.nativeElement.querySelector('[data-testid="tooth-13"]');

      start.dispatchEvent(new Event('pointerdown', { bubbles: true }));
      mid.dispatchEvent(new Event('pointerenter', { bubbles: true }));
      end.dispatchEvent(new Event('pointerenter', { bubbles: true }));
      fixture.detectChanges();

      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([11, 12, 13]);
    });

    it('clearing the selection requires the explicit "Wyczyść zaznaczenie" action', () => {
      fixture.nativeElement.querySelector('[data-testid="tooth-11"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();
      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([11]);

      // toggling multi-select mode off and back on must not silently clear it
      fixture.nativeElement.querySelector('[data-testid="multi-select-toggle"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();
      fixture.nativeElement.querySelector('[data-testid="multi-select-toggle"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();
      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([11]);

      fixture.nativeElement.querySelector('[data-testid="clear-selection"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();
      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([]);
    });

    it('right-click on a selected tooth opens the quick context-menu without altering the multi-selection (T122/FR-020b)', () => {
      fixture.nativeElement.querySelector('[data-testid="tooth-11"]').dispatchEvent(new Event('click'));
      fixture.nativeElement.querySelector('[data-testid="tooth-12"]').dispatchEvent(new Event('click'));
      fixture.detectChanges();
      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([11, 12]);

      fixture.nativeElement
        .querySelector('[data-testid="tooth-11"]')
        .dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true }));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="tooth-context-menu"]')).toBeTruthy();
      expect(fixture.componentInstance.selectedFdiNumbers()).toEqual([11, 12]);
    });
  });
});
