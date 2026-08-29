import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth.service';
import { LoginComponent } from './login.component';
import { LoginFlowState } from './login-flow-state';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authService: { login: ReturnType<typeof vi.fn> };
  let router: Router;
  let loginFlowState: LoginFlowState;

  beforeEach(async () => {
    authService = { login: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: AuthService, useValue: authService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    loginFlowState = TestBed.inject(LoginFlowState);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  it('keeps the submit action disabled until email and password are valid', () => {
    expect(component.form.invalid).toBe(true);

    component.form.setValue({ email: 'reception@dentalclinic.example', password: 'correct-horse-battery' });

    expect(component.form.valid).toBe(true);
  });

  it('on successful login, hands the pre-auth token to LoginFlowState and navigates to the MFA step', () => {
    authService.login.mockReturnValue(
      of({ preAuthToken: 'token-abc', mfaRequired: true, mfaSecret: null, mfaOtpAuthUri: null }),
    );
    component.form.setValue({ email: 'reception@dentalclinic.example', password: 'correct-horse-battery' });

    component.submit();

    expect(authService.login).toHaveBeenCalledWith('reception@dentalclinic.example', 'correct-horse-battery');
    expect(loginFlowState.current()).toEqual({
      preAuthToken: 'token-abc',
      mfaSecret: null,
      mfaOtpAuthUri: null,
    });
    expect(router.navigate).toHaveBeenCalledWith(['/login/mfa']);
  });

  it('on a 401 response, shows an error and does not navigate', () => {
    authService.login.mockReturnValue(throwError(() => ({ status: 401 })));
    component.form.setValue({ email: 'reception@dentalclinic.example', password: 'wrong-password12' });

    component.submit();

    expect(component.errorMessage()).toContain('Nieprawidłowy');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('on a 423 response, shows a lockout-specific message', () => {
    authService.login.mockReturnValue(throwError(() => ({ status: 423 })));
    component.form.setValue({ email: 'reception@dentalclinic.example', password: 'correct-horse-battery' });

    component.submit();

    expect(component.errorMessage()).toContain('zablokowane');
  });

  it('renders the brand mark with an accessible name (FR-023)', () => {
    fixture.detectChanges();

    const mark = fixture.nativeElement.querySelector('[data-testid="brand-mark"]');

    expect(mark).toBeTruthy();
    expect(mark.getAttribute('role')).toBe('img');
    expect(mark.getAttribute('aria-label')).toBe('Projekt Uśmiech');
  });
});
