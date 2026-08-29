import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { beforeEach, describe, expect, it } from 'vitest';
import { ThemeToggleComponent } from './theme-toggle.component';
import { ThemeService } from './theme.service';

describe('ThemeToggleComponent', () => {
  let fixture: ComponentFixture<ThemeToggleComponent>;
  let themeService: ThemeService;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [ThemeToggleComponent],
      providers: [provideAnimationsAsync()],
    }).compileComponents();

    fixture = TestBed.createComponent(ThemeToggleComponent);
    themeService = TestBed.inject(ThemeService);
    fixture.detectChanges();
  });

  function toggleButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="theme-toggle"]');
  }

  it('renders a control with data-testid="theme-toggle" (contracts/theme-preference.md §1)', () => {
    expect(toggleButton()).toBeTruthy();
  });

  it('is a real <button>, reachable by keyboard without a tabindex hack', () => {
    const el = toggleButton();
    expect(el.tagName.toLowerCase()).toBe('button');
    expect(el.hasAttribute('disabled')).toBe(false);
  });

  it('has an accessible name that communicates the current state and the effect of activation (FR-015)', () => {
    const label = toggleButton().getAttribute('aria-label') ?? '';
    expect(label.length).toBeGreaterThan(0);
    // motyw jasny aktywny -> nazwa musi mówić, co się stanie po kliknięciu (włączy ciemny)
    expect(label.toLowerCase()).toContain('ciemny');
  });

  it('updates its accessible name after toggling (state changes, so does the announced effect)', () => {
    const before = toggleButton().getAttribute('aria-label');

    toggleButton().click();
    fixture.detectChanges();

    const after = toggleButton().getAttribute('aria-label');
    expect(after).not.toBe(before);
    expect((after ?? '').toLowerCase()).toContain('jasny');
  });

  it('clicking the control calls ThemeService.toggle()', () => {
    expect(themeService.resolved()).toBe('light');

    toggleButton().click();
    fixture.detectChanges();

    expect(themeService.resolved()).toBe('dark');
  });
});
