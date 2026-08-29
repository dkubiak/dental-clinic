import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PatientsService } from '../patients.service';
import { ToothStateEntry } from '../patients.models';
import { FDI_TOOTH_NUMBERS, ToothChartComponent } from './tooth-chart.component';

describe('ToothChartComponent', () => {
  let fixture: ComponentFixture<ToothChartComponent>;
  let component: ToothChartComponent;
  let patientsService: {
    getToothChart: ReturnType<typeof vi.fn>;
    setToothStatus: ReturnType<typeof vi.fn>;
  };

  const healthyChart: ToothStateEntry[] = FDI_TOOTH_NUMBERS.map((toothNumber) => ({
    toothNumber,
    status: 'HEALTHY',
    updatedAt: null,
  }));

  beforeEach(async () => {
    patientsService = {
      getToothChart: vi.fn().mockReturnValue(of(healthyChart)),
      setToothStatus: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ToothChartComponent],
      providers: [{ provide: PatientsService, useValue: patientsService }],
    }).compileComponents();

    fixture = TestBed.createComponent(ToothChartComponent);
    component = fixture.componentInstance;
    component.patientId = 'p1';
    fixture.detectChanges();
  });

  it('renders the jaw SVG with all 32 teeth', () => {
    expect(patientsService.getToothChart).toHaveBeenCalledWith('p1');
    const teethEls = fixture.nativeElement.querySelectorAll('[data-testid^="tooth-"]');
    expect(teethEls.length).toBe(32);
  });

  it('tapping a tooth selects it', () => {
    fixture.nativeElement.querySelector('[data-testid="tooth-11"]').dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(component.selectedTooth()).toBe(11);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('11');
  });

  it('marks a sick tooth with a signal other than fill color (FR-019) — fills alone are 1.23:1/1.09:1, indistinguishable', () => {
    patientsService.getToothChart.mockReturnValue(
      of(
        healthyChart.map((t) =>
          t.toothNumber === 11 ? { ...t, status: 'SICK' as const } : t,
        ),
      ),
    );
    fixture = TestBed.createComponent(ToothChartComponent);
    component = fixture.componentInstance;
    component.patientId = 'p1';
    fixture.detectChanges();

    const healthyTooth = fixture.nativeElement.querySelector('[data-testid="tooth-12"]');
    const sickTooth = fixture.nativeElement.querySelector('[data-testid="tooth-11"]');

    // The non-color signal must be a real attribute difference — a class name alone (which only
    // exists to carry a color rule) would not satisfy "an attribute other than fill".
    expect(sickTooth.getAttribute('stroke-dasharray')).not.toBe(
      healthyTooth.getAttribute('stroke-dasharray'),
    );
    expect(sickTooth.getAttribute('stroke-dasharray')).toBeTruthy();
  });

  it('toggling the selected tooth updates its visual (and data) state', () => {
    patientsService.setToothStatus.mockReturnValue(
      of({ toothNumber: 11, status: 'SICK', updatedAt: '2026-01-01T00:00:00Z' }),
    );

    fixture.nativeElement.querySelector('[data-testid="tooth-11"]').dispatchEvent(new Event('click'));
    fixture.detectChanges();
    fixture.nativeElement
      .querySelector('[data-testid="toggle-status"]')
      .dispatchEvent(new Event('click'));
    fixture.detectChanges();

    expect(patientsService.setToothStatus).toHaveBeenCalledWith('p1', 11, 'SICK');
    expect(component.teeth().find((t) => t.toothNumber === 11)?.status).toBe('SICK');
    expect(
      fixture.nativeElement.querySelector('[data-testid="tooth-11"]').classList.contains('sick'),
    ).toBe(true);
  });
});
