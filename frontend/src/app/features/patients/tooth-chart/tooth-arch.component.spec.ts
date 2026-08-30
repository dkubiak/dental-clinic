import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { RootCanal, ToothFinding, ToothPosition } from '../patients.models';
import { ToothArchComponent } from './tooth-arch.component';

function activeFinding(overrides: Partial<ToothFinding> = {}): ToothFinding {
  return {
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
    ...overrides,
  };
}

function position(
  fdiNumber: number,
  currentFindings: ToothFinding[],
  canals: RootCanal[] = [],
): ToothPosition {
  return {
    fdiNumber,
    dentitionType: 'PERMANENT',
    toothType: 'MOLAR',
    presence: 'PRESENT',
    presenceDate: null,
    version: 0,
    canals,
    currentFindings,
  };
}

describe('ToothArchComponent', () => {
  let fixture: ComponentFixture<ToothArchComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ToothArchComponent] }).compileComponents();
    fixture = TestBed.createComponent(ToothArchComponent);
    fixture.componentRef.setInput('dir', 1);
  });

  it('shows the "wiele wpisów" indicator for a tooth whose findings cannot all render (FR-010, edge case)', () => {
    const overloaded = position(11, [
      activeFinding({ id: 'f1', surfaces: ['MESIAL'] }),
      activeFinding({ id: 'f2', surfaces: ['DISTAL'] }),
      activeFinding({ id: 'f3', surfaces: ['VESTIBULAR'] }),
      activeFinding({ id: 'f4', surfaces: ['LINGUAL_PALATAL'] }),
    ]);
    fixture.componentRef.setInput('positions', [overloaded]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="tooth-11-multi-indicator"]'),
    ).toBeTruthy();
  });

  it('does not show the indicator for a tooth with a single finding', () => {
    fixture.componentRef.setInput('positions', [position(11, [activeFinding()])]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="tooth-11-multi-indicator"]'),
    ).toBeFalsy();
  });

  it('renders canal treatment-state colors (red/green/green-with-red-apex) inside the root silhouette (T094/FR-066a)', () => {
    const canals: RootCanal[] = [
      { id: 'c1', name: 'A', state: 'NEEDS_TREATMENT', removed: false, version: 0 },
      { id: 'c2', name: 'B', state: 'TREATED', removed: false, version: 0 },
      { id: 'c3', name: 'C', state: 'UNDERTREATED', removed: false, version: 0 },
      { id: 'c4', name: 'D (removed)', state: 'TREATED', removed: true, version: 0 },
    ];
    fixture.componentRef.setInput('positions', [position(11, [], canals)]);
    fixture.detectChanges();

    const tooth = fixture.nativeElement.querySelector('[data-testid="tooth-11"]');
    expect(tooth.querySelectorAll('.canal.c-treat').length).toBeGreaterThan(0);
    expect(tooth.querySelectorAll('.canal.c-done').length).toBeGreaterThan(0);
    // UNDERTREATED renders a body(green)+apex(red) split — the split itself is the non-color cue.
    expect(tooth.querySelectorAll('.canal.c-under-body').length).toBeGreaterThan(0);
    expect(tooth.querySelectorAll('.canal.c-under-apex').length).toBeGreaterThan(0);
  });

  it('distinguishes healthy/diseased/restored by more than color — outline weight/pattern (T129/FR-039/FR-050)', () => {
    const diseased = activeFinding({ diagnosisCatalogEntry: { ...activeFinding().diagnosisCatalogEntry, layer: 'DIAGNOSIS' } });
    const restored = activeFinding({
      id: 'f2',
      diagnosisCatalogEntry: { ...activeFinding().diagnosisCatalogEntry, layer: 'EXISTING_STATE' },
    });
    fixture.componentRef.setInput('positions', [
      position(11, [diseased]),
      position(12, [restored]),
      position(13, []),
    ]);
    fixture.detectChanges();

    const diseasedCrown = fixture.nativeElement.querySelector('[data-testid="tooth-11"] .crown');
    const restoredCrown = fixture.nativeElement.querySelector('[data-testid="tooth-12"] .crown');
    const healthyCrown = fixture.nativeElement.querySelector('[data-testid="tooth-13"] .crown');

    const diseasedStyle = getComputedStyle(diseasedCrown);
    const restoredStyle = getComputedStyle(restoredCrown);
    const healthyStyle = getComputedStyle(healthyCrown);

    // diseased: thicker outline than healthy; restored: dashed outline, healthy: none.
    expect(parseFloat(diseasedStyle.strokeWidth)).toBeGreaterThan(parseFloat(healthyStyle.strokeWidth));
    expect(restoredStyle.strokeDasharray).not.toBe('none');
    expect(healthyStyle.strokeDasharray === 'none' || healthyStyle.strokeDasharray === '').toBe(true);
  });

  it('exposes multi-selection state via aria-pressed only while a selection is active', () => {
    fixture.componentRef.setInput('positions', [position(11, [])]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="tooth-11"]').hasAttribute('aria-pressed')).toBe(false);

    fixture.componentRef.setInput('selectedFdiNumbers', [11]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="tooth-11"]').getAttribute('aria-pressed')).toBe('true');
  });
});
