import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DiagnosisCatalogEntry, ToothFinding } from '../patients.models';
import { DiagnosisCatalogService } from './diagnosis-catalog.service';
import { ToothChartService } from './tooth-chart.service';
import { ToothContextMenuComponent } from './tooth-context-menu.component';

const CARIES: DiagnosisCatalogEntry = {
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
};

const EXTRACTED: DiagnosisCatalogEntry = {
  id: 'dx2',
  code: 'EXTR',
  namePl: 'Ząb usunięty (stan po ekstrakcji)',
  category: 'ERUPTION_MISSING',
  anatomicalScope: 'WHOLE_TOOTH',
  layer: 'EXISTING_STATE',
  icd10Code: null,
  severityOptions: null,
  allowedForMissingTooth: true,
  deciduousAllowed: true,
  quickAccess: true,
  requiresFreeText: false,
};

function finding(overrides: Partial<ToothFinding> = {}): ToothFinding {
  return {
    id: 'f1',
    fdiNumber: 36,
    diagnosisCatalogEntry: CARIES,
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
    ...overrides,
  };
}

describe('ToothContextMenuComponent', () => {
  let fixture: ComponentFixture<ToothContextMenuComponent>;
  let toothChartService: {
    addFinding: ReturnType<typeof vi.fn>;
    closeFinding: ReturnType<typeof vi.fn>;
    addFindingsBulk: ReturnType<typeof vi.fn>;
  };
  let diagnosisCatalogService: {
    search: ReturnType<typeof vi.fn>;
    recentEntries: ReturnType<typeof vi.fn>;
    withRecencyTracking: ReturnType<typeof vi.fn>;
  };

  async function setup(inputs: Record<string, unknown> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ToothContextMenuComponent],
      providers: [
        { provide: ToothChartService, useValue: toothChartService },
        { provide: DiagnosisCatalogService, useValue: diagnosisCatalogService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ToothContextMenuComponent);
    fixture.componentRef.setInput('open', true);
    fixture.componentRef.setInput('patientId', 'p1');
    fixture.componentRef.setInput('fdiNumber', 36);
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    fixture.detectChanges();
  }

  beforeEach(() => {
    toothChartService = {
      addFinding: vi.fn().mockReturnValue(of(finding())),
      closeFinding: vi.fn(),
      addFindingsBulk: vi.fn().mockReturnValue(of({ created: [finding()], skipped: [] })),
    };
    diagnosisCatalogService = {
      search: vi.fn().mockReturnValue(of([CARIES, EXTRACTED])),
      recentEntries: vi.fn().mockReturnValue([]),
      withRecencyTracking: vi.fn((_code: string, obs) => obs),
    };
  });

  it('lists quick-access entries applicable without a target surface (SURFACE-scope excluded)', async () => {
    await setup();

    expect(fixture.nativeElement.querySelector('[data-testid="context-menu-quick-K02.1"]')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[data-testid="context-menu-quick-EXTR"]')).toBeTruthy();
  });

  it('includes SURFACE-scope entries when invoked on a specific surface zone', async () => {
    await setup({ targetSurface: 'MESIAL' });

    expect(fixture.nativeElement.querySelector('[data-testid="context-menu-quick-K02.1"]')).toBeTruthy();
  });

  it('saves the chosen entry immediately via the same addFinding path as the full form, on a single tooth (no multi-selection required)', async () => {
    await setup({ targetSurface: 'MESIAL' });
    let savedFinding: ToothFinding | undefined;
    fixture.componentInstance.saved.subscribe((f: ToothFinding) => (savedFinding = f));

    fixture.nativeElement.querySelector('[data-testid="context-menu-quick-K02.1"]').dispatchEvent(new Event('click'));

    expect(toothChartService.addFinding).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ fdiNumber: 36, diagnosisCatalogEntryId: 'dx1', surfaces: ['MESIAL'] }),
    );
    expect(savedFinding).toBeTruthy();
  });

  it('offers an instant undo implemented as a correct-supersede (close) call', async () => {
    toothChartService.closeFinding.mockReturnValue(of(finding({ id: 'f2', supersedesFindingId: 'f1' })));
    await setup({ undoTarget: finding() });

    const undoButton = fixture.nativeElement.querySelector('[data-testid="context-menu-undo"]');
    expect(undoButton).toBeTruthy();
    undoButton.dispatchEvent(new Event('click'));

    expect(toothChartService.closeFinding).toHaveBeenCalledWith(
      'p1',
      'f1',
      expect.objectContaining({ resolvedDate: expect.any(String) }),
    );
  });

  it('applies the chosen entry to every selected position via the bulk path when a multi-selection is active (T115/FR-020b)', async () => {
    await setup({ targetSurface: 'MESIAL', selectedFdiNumbers: [11, 12, 13] });

    fixture.nativeElement.querySelector('[data-testid="context-menu-quick-K02.1"]').dispatchEvent(new Event('click'));

    expect(toothChartService.addFindingsBulk).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ fdiNumbers: [11, 12, 13], diagnosisCatalogEntryId: 'dx1' }),
    );
    expect(toothChartService.addFinding).not.toHaveBeenCalled();
  });

  it('reports skipped positions after a bulk save instead of closing silently (T115/FR-020b)', async () => {
    toothChartService.addFindingsBulk.mockReturnValue(
      of({ created: [finding({ fdiNumber: 11 })], skipped: [{ fdiNumber: 12, reason: 'Brak zęba.' }] }),
    );
    let closedCount = 0;
    await setup({ targetSurface: 'MESIAL', selectedFdiNumbers: [11, 12] });
    fixture.componentInstance.closed.subscribe(() => closedCount++);

    fixture.nativeElement.querySelector('[data-testid="context-menu-quick-K02.1"]').dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(closedCount).toBe(0); // stays open to report the skip
    const skippedSection = fixture.nativeElement.querySelector('[data-testid="context-menu-skipped"]');
    expect(skippedSection).toBeTruthy();
    expect(skippedSection.textContent).toContain('12');
    expect(skippedSection.textContent).toContain('Brak zęba.');
  });

  it('does not alter the current selection when opened, single-tooth path is unaffected without a multi-selection', async () => {
    await setup({ targetSurface: 'MESIAL' }); // selectedFdiNumbers defaults to []

    fixture.nativeElement.querySelector('[data-testid="context-menu-quick-K02.1"]').dispatchEvent(new Event('click'));

    expect(toothChartService.addFinding).toHaveBeenCalled();
    expect(toothChartService.addFindingsBulk).not.toHaveBeenCalled();
  });
});
