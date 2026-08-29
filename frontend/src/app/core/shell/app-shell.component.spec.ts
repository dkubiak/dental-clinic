import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthState } from '../auth/auth-state';
import { AppShellComponent } from './app-shell.component';

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;
  let authState: AuthState;

  async function setup(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [provideRouter([]), provideAnimationsAsync()],
    }).compileComponents();

    fixture = TestBed.createComponent(AppShellComponent);
    authState = TestBed.inject(AuthState);
  }

  it('renders a persistent toolbar', async () => {
    await setup();
    authState.setRole('RECEPTION');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('mat-toolbar')).toBeTruthy();
  });

  it('renders role-aware nav — RECEPTION sees the patient-search link', async () => {
    await setup();
    authState.setRole('RECEPTION');
    fixture.detectChanges();

    const navText = fixture.nativeElement.textContent as string;
    expect(navText).toContain('Pacjenci');
  });

  it('shows the "Nowy pacjent" primary action for RECEPTION (FR-001)', async () => {
    await setup();
    authState.setRole('RECEPTION');
    fixture.detectChanges();

    const newPatientButton = fixture.nativeElement.querySelector(
      '[data-testid="new-patient-action"]',
    );
    expect(newPatientButton).toBeTruthy();
  });

  it('shows the "Nowy pacjent" primary action for DOCTOR (FR-001)', async () => {
    await setup();
    authState.setRole('DOCTOR');
    fixture.detectChanges();

    const newPatientButton = fixture.nativeElement.querySelector(
      '[data-testid="new-patient-action"]',
    );
    expect(newPatientButton).toBeTruthy();
  });

  it('hides the "Nowy pacjent" primary action for ASSISTANT (no create access, FR-001)', async () => {
    await setup();
    authState.setRole('ASSISTANT');
    fixture.detectChanges();

    const newPatientButton = fixture.nativeElement.querySelector(
      '[data-testid="new-patient-action"]',
    );
    expect(newPatientButton).toBeFalsy();
  });

  it('renders the brand mark in the toolbar with an accessible name (FR-023)', async () => {
    await setup();
    authState.setRole('RECEPTION');
    fixture.detectChanges();

    const mark = fixture.nativeElement.querySelector('[data-testid="brand-mark"]');

    expect(mark).toBeTruthy();
    expect(mark.getAttribute('role')).toBe('img');
    expect(mark.getAttribute('aria-label')).toBe('Projekt Uśmiech');
  });
});
