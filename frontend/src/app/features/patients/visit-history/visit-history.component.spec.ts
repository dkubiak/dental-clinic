import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PatientsService } from '../patients.service';
import { VisitHistoryComponent } from './visit-history.component';

describe('VisitHistoryComponent', () => {
  let fixture: ComponentFixture<VisitHistoryComponent>;
  let patientsService: { getVisitHistory: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    patientsService = { getVisitHistory: vi.fn().mockReturnValue(of([])) };

    await TestBed.configureTestingModule({
      imports: [VisitHistoryComponent],
      providers: [{ provide: PatientsService, useValue: patientsService }],
    }).compileComponents();

    fixture = TestBed.createComponent(VisitHistoryComponent);
    fixture.componentInstance.patientId = 'p1';
    fixture.detectChanges();
  });

  it('renders an empty-state message', () => {
    expect(patientsService.getVisitHistory).toHaveBeenCalledWith('p1');
    const text = fixture.nativeElement.textContent as string;
    expect(text).toMatch(/histori[ai]/i);
  });

  it('offers no add-entry control anywhere in the DOM (US3 Acceptance Scenario 2)', () => {
    const buttons = fixture.nativeElement.querySelectorAll('button, a');
    expect(buttons.length).toBe(0);
  });
});
