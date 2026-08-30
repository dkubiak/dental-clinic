import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DiagnosisCatalogEntry, RootCanal, ToothFinding, ToothPosition } from '../patients.models';
import { DiagnosisCatalogService } from './diagnosis-catalog.service';
import { ToothChartService } from './tooth-chart.service';
import { ToothDetailPanelComponent } from './tooth-detail-panel.component';

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

const PULPITIS: DiagnosisCatalogEntry = {
  id: 'dx2',
  code: 'K04.0i',
  namePl: 'Zapalenie miazgi nieodwracalne',
  category: 'PULP_PERIAPICAL',
  anatomicalScope: 'WHOLE_TOOTH',
  layer: 'DIAGNOSIS',
  icd10Code: 'K04.0',
  severityOptions: null,
  allowedForMissingTooth: false,
  deciduousAllowed: true,
  quickAccess: false,
  requiresFreeText: false,
};

function freshPosition(fdiNumber: number): ToothPosition {
  return {
    fdiNumber,
    dentitionType: 'PERMANENT',
    toothType: 'MOLAR',
    presence: 'PRESENT',
    presenceDate: null,
    version: 0,
    canals: [],
    currentFindings: [],
  };
}

function activeFinding(overrides: Partial<ToothFinding> = {}): ToothFinding {
  return {
    id: 'f1',
    fdiNumber: 36,
    diagnosisCatalogEntry: CARIES,
    surfaces: ['MESIAL'],
    rootCanalId: null,
    severity: null,
    freeTextDescription: null,
    note: null,
    diagnosisDate: '2026-01-01',
    resolvedDate: null,
    clinicalStatus: 'ACTIVE',
    recordStatus: 'CURRENT',
    supersedesFindingId: null,
    authorAccountId: 'a1',
    authorRole: 'DOCTOR',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('ToothDetailPanelComponent', () => {
  let fixture: ComponentFixture<ToothDetailPanelComponent>;
  let toothChartService: {
    addFinding: ReturnType<typeof vi.fn>;
    getPositionHistory: ReturnType<typeof vi.fn>;
    closeFinding: ReturnType<typeof vi.fn>;
    correctFinding: ReturnType<typeof vi.fn>;
    changePresence: ReturnType<typeof vi.fn>;
    addCanal: ReturnType<typeof vi.fn>;
    updateCanal: ReturnType<typeof vi.fn>;
    removeCanal: ReturnType<typeof vi.fn>;
  };
  let diagnosisCatalogService: {
    search: ReturnType<typeof vi.fn>;
    withRecencyTracking: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    toothChartService = {
      addFinding: vi.fn(),
      getPositionHistory: vi.fn().mockReturnValue(of([])),
      closeFinding: vi.fn().mockReturnValue(of(activeFinding())),
      correctFinding: vi.fn().mockReturnValue(of(activeFinding())),
      changePresence: vi.fn().mockReturnValue(of(freshPosition(36))),
      addCanal: vi.fn().mockReturnValue(of({ id: 'c1', name: 'MB', state: 'NEEDS_TREATMENT', removed: false, version: 0 })),
      updateCanal: vi.fn().mockReturnValue(of({ id: 'c1', name: 'MB', state: 'TREATED', removed: false, version: 1 })),
      removeCanal: vi.fn().mockReturnValue(of(undefined)),
    };
    diagnosisCatalogService = {
      search: vi.fn().mockReturnValue(of([CARIES, PULPITIS])),
      withRecencyTracking: vi.fn((_code: string, obs) => obs),
    };

    await TestBed.configureTestingModule({
      imports: [ToothDetailPanelComponent],
      providers: [
        { provide: ToothChartService, useValue: toothChartService },
        { provide: DiagnosisCatalogService, useValue: diagnosisCatalogService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ToothDetailPanelComponent);
    fixture.componentRef.setInput('patientId', 'p1');
    fixture.componentRef.setInput('fdiNumber', 36);
    fixture.componentRef.setInput('position', freshPosition(36));
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows FDI number, anatomical name, surface map, and an empty finding list for a fresh tooth (Scenario 2)', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('36');
    expect(text).toContain('pierwszy trzonowiec dolny lewy');
    expect(fixture.nativeElement.querySelector('app-surface-map')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="finding-list-empty"]')).toBeTruthy();
  });

  it('blocks save without a surface for a SURFACE-scope entry (Scenario 3)', () => {
    fixture.componentInstance.selectEntry(CARIES);
    fixture.detectChanges();

    const saveButton = fixture.nativeElement.querySelector('[data-testid="save-finding"]');
    expect(saveButton.disabled).toBe(true);

    fixture.componentInstance.toggleSurface('MESIAL');
    fixture.detectChanges();

    expect(saveButton.disabled).toBe(false);
  });

  it('hides the surface picker for a WHOLE_TOOTH-scope entry (Scenario 7)', () => {
    fixture.componentInstance.selectEntry(PULPITIS);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="surface-picker"]')).toBeFalsy();
    const saveButton = fixture.nativeElement.querySelector('[data-testid="save-finding"]');
    expect(saveButton.disabled).toBe(false);
  });

  it('renders side-by-side/drawer content that is never hidden regardless of layout (FR-006)', () => {
    const panel = fixture.nativeElement.querySelector('.detail-panel');
    expect(panel).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="catalog-search-input"]')).toBeTruthy();
  });

  it('asks for confirmation before discarding an unsaved form (FR-055)', () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    fixture.componentInstance.selectEntry(CARIES);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="discard-finding"]').dispatchEvent(new Event('click'));

    expect(confirmSpy).toHaveBeenCalled();
    // user declined — the selection must still be present
    expect(fixture.componentInstance.selectedEntry()).toBe(CARIES);
  });

  it('keeps entered field values after a failed save (FR-071)', () => {
    toothChartService.addFinding.mockReturnValue(throwError(() => new Error('boom')));
    fixture.componentInstance.selectEntry(CARIES);
    fixture.componentInstance.toggleSurface('MESIAL');
    fixture.componentInstance.note.set('Ważna notatka');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="save-error"]')).toBeTruthy();
    expect(fixture.componentInstance.selectedEntry()).toBe(CARIES);
    expect(fixture.componentInstance.note()).toBe('Ważna notatka');
  });

  it('shows a visible, no-scroll-required confirmation on a successful save (FR-056)', () => {
    toothChartService.addFinding.mockReturnValue(
      of({
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
      }),
    );
    fixture.componentInstance.selectEntry(CARIES);
    fixture.componentInstance.toggleSurface('MESIAL');
    fixture.detectChanges();

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const successEl = fixture.nativeElement.querySelector('[data-testid="save-success"]');
    expect(successEl).toBeTruthy();
    expect(successEl.getAttribute('role')).toBe('status');
  });

  it('the historia zęba disclosure is collapsed by default and loads resolved/superseded entries only on expansion (FR-034)', () => {
    const resolved = {
      id: 'f1',
      fdiNumber: 36,
      diagnosisCatalogEntry: CARIES,
      surfaces: ['MESIAL'],
      rootCanalId: null,
      severity: null,
      freeTextDescription: null,
      note: null,
      diagnosisDate: '2026-01-01',
      resolvedDate: '2026-08-30',
      clinicalStatus: 'RESOLVED',
      recordStatus: 'SUPERSEDED',
      supersedesFindingId: null,
      authorAccountId: 'a1',
      authorRole: 'DOCTOR',
      createdAt: '2026-01-01T00:00:00Z',
    };
    const current = { ...resolved, id: 'f2', recordStatus: 'CURRENT', supersedesFindingId: 'f1' };
    toothChartService.getPositionHistory.mockReturnValue(of([resolved, current]));

    expect(
      fixture.nativeElement.querySelector('[data-testid="history-item"]'),
    ).toBeFalsy();
    expect(toothChartService.getPositionHistory).not.toHaveBeenCalled();

    fixture.nativeElement
      .querySelector('[data-testid="history-toggle"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(toothChartService.getPositionHistory).toHaveBeenCalledWith('p1', 36);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="history-item"]').length).toBe(2);
  });

  it('"Zamknij rozpoznanie" requires a resolvedDate (T082)', () => {
    const finding = activeFinding();
    fixture.componentRef.setInput('position', {
      ...freshPosition(36),
      currentFindings: [finding],
    });
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="close-finding-f1"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    const submitButton = fixture.nativeElement.querySelector('[data-testid="close-submit"]');
    expect(submitButton.disabled).toBe(true);

    fixture.componentInstance.closeResolvedDate.set('2026-08-30');
    fixture.detectChanges();
    expect(submitButton.disabled).toBe(false);

    fixture.nativeElement
      .querySelector('[data-testid="close-form"]')
      .dispatchEvent(new Event('submit'));

    expect(toothChartService.closeFinding).toHaveBeenCalledWith('p1', 'f1', {
      resolvedDate: '2026-08-30',
    });
  });

  it('"Koryguj" pre-fills the current values and submits through correctFinding with the original id (T082)', () => {
    const finding = activeFinding({ note: 'Stara notatka' });
    fixture.componentRef.setInput('position', {
      ...freshPosition(36),
      currentFindings: [finding],
    });
    toothChartService.correctFinding.mockReturnValue(of({ ...finding, id: 'f2', supersedesFindingId: 'f1' }));
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="correct-finding-f1"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedEntry()).toBe(CARIES);
    expect(fixture.componentInstance.selectedSurfaces()).toEqual(['MESIAL']);
    expect(fixture.componentInstance.note()).toBe('Stara notatka');

    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    expect(toothChartService.correctFinding).toHaveBeenCalledWith(
      'p1',
      'f1',
      expect.objectContaining({ diagnosisCatalogEntryId: CARIES.id, note: 'Stara notatka' }),
    );
  });

  it('presence controls mark a tooth extracted/congenitally-missing/unerupted (T093/FR-039)', () => {
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="presence-EXTRACTED"]')
      .dispatchEvent(new Event('click'));

    expect(toothChartService.changePresence).toHaveBeenCalledWith(
      'p1',
      36,
      expect.objectContaining({ presence: 'EXTRACTED', expectedVersion: 0 }),
    );

    fixture.nativeElement
      .querySelector('[data-testid="presence-CONGENITALLY_MISSING"]')
      .dispatchEvent(new Event('click'));
    expect(toothChartService.changePresence).toHaveBeenCalledWith(
      'p1',
      36,
      expect.objectContaining({ presence: 'CONGENITALLY_MISSING' }),
    );

    fixture.nativeElement
      .querySelector('[data-testid="presence-UNERUPTED"]')
      .dispatchEvent(new Event('click'));
    expect(toothChartService.changePresence).toHaveBeenCalledWith(
      'p1',
      36,
      expect.objectContaining({ presence: 'UNERUPTED' }),
    );
  });

  it('presence buttons show aria-pressed for the current presence', () => {
    fixture.componentRef.setInput('position', { ...freshPosition(36), presence: 'EXTRACTED' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="presence-EXTRACTED"]').getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      fixture.nativeElement.querySelector('[data-testid="presence-PRESENT"]').getAttribute('aria-pressed'),
    ).toBe('false');
  });

  it('root-canal add/rename/state-change/remove controls call the matching service methods (T094)', () => {
    const canal: RootCanal = { id: 'c1', name: 'MB', state: 'NEEDS_TREATMENT', removed: false, version: 0 };
    fixture.componentRef.setInput('position', { ...freshPosition(36), canals: [canal] });
    fixture.detectChanges();

    // rename
    const nameInput = fixture.nativeElement.querySelector('[data-testid="canal-name-c1"]');
    nameInput.value = 'MB2';
    nameInput.dispatchEvent(new Event('change'));
    expect(toothChartService.updateCanal).toHaveBeenCalledWith('p1', 36, 'c1', {
      name: 'MB2',
      expectedVersion: 0,
    });

    // state change
    fixture.nativeElement
      .querySelector('[data-testid="canal-state-c1-TREATED"]')
      .dispatchEvent(new Event('click'));
    expect(toothChartService.updateCanal).toHaveBeenCalledWith('p1', 36, 'c1', {
      state: 'TREATED',
      expectedVersion: 0,
    });

    // remove
    fixture.nativeElement.querySelector('[data-testid="canal-remove-c1"]').dispatchEvent(new Event('click'));
    expect(toothChartService.removeCanal).toHaveBeenCalledWith('p1', 36, 'c1');

    // add — FR-064: no free-text input, a one-click suggestion instead (fdi 36 is a lower first
    // molar; with one canal already present, the next suggestion is "bliższy językowy (ML)").
    fixture.nativeElement.querySelector('[data-testid="add-canal-submit"]').dispatchEvent(new Event('click'));
    expect(toothChartService.addCanal).toHaveBeenCalledWith('p1', 36, { name: 'bliższy językowy (ML)' });
  });

  it('shows a "typowo dla tego zęba" suggestion and an "add typical" shortcut only while no canals exist yet (FR-064)', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="canal-suggestion-hint"]').textContent).toContain(
      'bliższy policzkowy (MB)',
    );
    const addTypical = fixture.nativeElement.querySelector('[data-testid="add-typical-canals"]');
    expect(addTypical).toBeTruthy();

    addTypical.dispatchEvent(new Event('click'));
    expect(toothChartService.addCanal).toHaveBeenNthCalledWith(1, 'p1', 36, { name: 'bliższy policzkowy (MB)' });
  });

  it('the add-canal form disappears once 6 non-removed canals exist', () => {
    const canals: RootCanal[] = Array.from({ length: 6 }, (_, i) => ({
      id: `c${i}`,
      name: `Kanał ${i}`,
      state: 'NEEDS_TREATMENT',
      removed: false,
      version: 0,
    }));
    fixture.componentRef.setInput('position', { ...freshPosition(36), canals });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="add-canal-form"]')).toBeFalsy();
  });
});
