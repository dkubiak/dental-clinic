import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth.service';
import { LoginFlowState } from './login-flow-state';
import { MfaChallengeComponent } from './mfa-challenge.component';

describe('MfaChallengeComponent', () => {
  let fixture: ComponentFixture<MfaChallengeComponent>;
  let component: MfaChallengeComponent;
  let authService: { verifyMfa: ReturnType<typeof vi.fn> };
  let router: Router;
  let loginFlowState: LoginFlowState;

  function createComponent() {
    fixture = TestBed.createComponent(MfaChallengeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    authService = { verifyMfa: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [MfaChallengeComponent],
      providers: [provideAnimationsAsync(), { provide: AuthService, useValue: authService }],
    }).compileComponents();

    router = TestBed.inject(Router);
    loginFlowState = TestBed.inject(LoginFlowState);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  it('redirects back to /login when there is no pending challenge (e.g. deep link/refresh)', () => {
    loginFlowState.clear();

    createComponent();

    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('shows the manual-entry secret when this is a first-time enrollment', () => {
    loginFlowState.start({
      preAuthToken: 'token-abc',
      mfaSecret: 'JBSWY3DPEHPK3PXP',
      mfaOtpAuthUri: 'otpauth://totp/DentalClinic:doctor@example?secret=JBSWY3DPEHPK3PXP',
    });

    createComponent();

    expect(fixture.nativeElement.textContent).toContain('JBSWY3DPEHPK3PXP');
  });

  it('on valid code, clears the pending challenge and navigates to the role home route', () => {
    loginFlowState.start({ preAuthToken: 'token-abc', mfaSecret: null, mfaOtpAuthUri: null });
    authService.verifyMfa.mockReturnValue(of({ role: 'DOCTOR' }));
    createComponent();

    component.form.setValue({ totpCode: '123456' });
    component.submit();

    expect(authService.verifyMfa).toHaveBeenCalledWith('token-abc', '123456');
    expect(loginFlowState.current()).toBeNull();
    // DOCTOR's role home is the shared patient-search screen (T040, US1) — not a per-role route.
    expect(router.navigate).toHaveBeenCalledWith(['/patients']);
  });

  it('on an invalid code, shows an error and keeps the challenge pending', () => {
    loginFlowState.start({ preAuthToken: 'token-abc', mfaSecret: null, mfaOtpAuthUri: null });
    authService.verifyMfa.mockReturnValue(throwError(() => ({ status: 401 })));
    createComponent();

    component.form.setValue({ totpCode: '000000' });
    component.submit();

    expect(component.errorMessage()).toContain('Nieprawidłowy');
    expect(loginFlowState.current()).not.toBeNull();
    expect(router.navigate).not.toHaveBeenCalledWith(['/patients']);
  });
});
