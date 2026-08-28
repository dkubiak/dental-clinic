import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { StatusIndicatorComponent, StatusType } from './status-indicator.component';

describe('StatusIndicatorComponent', () => {
  let fixture: ComponentFixture<StatusIndicatorComponent>;

  const cases: { type: StatusType; icon: string }[] = [
    { type: 'success', icon: 'check_circle' },
    { type: 'warning', icon: 'warning' },
    { type: 'error', icon: 'error' },
    { type: 'info', icon: 'info' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusIndicatorComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(StatusIndicatorComponent);
  });

  function render(type: StatusType, message: string): void {
    fixture.componentRef.setInput('type', type);
    fixture.componentRef.setInput('message', message);
    fixture.detectChanges();
  }

  it.each(cases)(
    'type=$type carries a text label AND a distinct icon, not just color (FR-019)',
    ({ type, icon }) => {
      render(type, 'Test message');

      const iconEl = fixture.nativeElement.querySelector('mat-icon');
      const text = (fixture.nativeElement.textContent as string).trim();

      // Second signal #1: icon glyph name differs per type.
      expect(iconEl?.textContent?.trim()).toBe(icon);
      // Second signal #2: the message itself is always rendered as text, independent of color.
      expect(text).toContain('Test message');
    },
  );

  it('every type maps to a distinct icon — no two types share the same non-color signal', () => {
    const seen = new Set<string>();
    for (const { type, icon } of cases) {
      render(type, 'x');
      expect(seen.has(icon)).toBe(false);
      seen.add(icon);
    }
  });

  it('removing the message leaves the icon as the sole non-color signal (still present)', () => {
    render('warning', '');
    const iconEl = fixture.nativeElement.querySelector('mat-icon');
    expect(iconEl?.textContent?.trim()).toBe('warning');
  });
});
