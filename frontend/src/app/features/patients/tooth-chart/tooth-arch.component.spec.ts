import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ToothFinding, ToothPosition } from '../patients.models';
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

function position(fdiNumber: number, currentFindings: ToothFinding[]): ToothPosition {
  return {
    fdiNumber,
    dentitionType: 'PERMANENT',
    toothType: 'MOLAR',
    presence: 'PRESENT',
    presenceDate: null,
    version: 0,
    canals: [],
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
});
