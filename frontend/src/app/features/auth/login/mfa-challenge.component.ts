import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/auth/auth.service';
import { roleHomeRoute } from '../../../core/auth/role-home-route';
import { LoginFlowState } from './login-flow-state';

/**
 * T050 — MFA challenge screen, step 2 of login. Also renders first-login enrollment setup
 * (manual-entry secret + otpauth:// URI, see MfaService.buildOtpAuthUri) when
 * {@link PendingMfaChallenge.mfaSecret} is present — the account has no prior enrollment
 * (data-model.md), so this same 6-digit-code form both finishes enrollment and completes login on
 * its first successful submission (AuthService.completeMfaLogin).
 */
@Component({
  selector: 'app-mfa-challenge',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="mfa-page">
      <mat-card class="mfa-card">
        <mat-card-header>
          <mat-card-title>Weryfikacja dwuskładnikowa</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (pendingChallenge()?.mfaSecret; as secret) {
            <div class="enrollment-setup">
              <p>
                To pierwsze logowanie na tym koncie — skonfiguruj aplikację uwierzytelniającą
                (np. Google Authenticator, Authy, 1Password):
              </p>
              <p>Wpisz ten klucz ręcznie w aplikacji:</p>
              <code class="secret">{{ secret }}</code>
            </div>
          }

          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>6-cyfrowy kod</mat-label>
              <input
                matInput
                inputmode="numeric"
                maxlength="6"
                formControlName="totpCode"
                autocomplete="one-time-code"
              />
            </mat-form-field>

            @if (errorMessage()) {
              <p class="error" role="alert">{{ errorMessage() }}</p>
            }

            <button
              mat-flat-button
              color="primary"
              type="submit"
              class="full-width"
              [disabled]="form.invalid || submitting()"
            >
              @if (submitting()) {
                <mat-spinner diameter="20" />
              } @else {
                Potwierdź
              }
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .mfa-page {
      display: flex;
      justify-content: center;
      padding: 16px;
    }
    .mfa-card {
      width: 100%;
      max-width: 420px;
    }
    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }
    .error {
      color: var(--mat-sys-error, #b3261e);
      margin: 0 0 12px;
    }
    .enrollment-setup {
      margin-bottom: 16px;
    }
    .secret {
      display: block;
      padding: 8px;
      background: var(--mat-sys-surface-container, #f2f2f2);
      word-break: break-all;
    }
  `,
})
export class MfaChallengeComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly loginFlowState = inject(LoginFlowState);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly pendingChallenge = this.loginFlowState.current;

  readonly form = this.formBuilder.nonNullable.group({
    totpCode: ['', [Validators.required, Validators.pattern(/^[0-9]{6}$/)]],
  });

  ngOnInit(): void {
    // Deep-linked directly (e.g. page refresh) without a pre-auth token in memory — the token is
    // deliberately never persisted (LoginFlowState javadoc), so the only correct recovery is to
    // restart the login flow.
    if (!this.pendingChallenge()) {
      this.router.navigate(['/login']);
    }
  }

  submit(): void {
    const challenge = this.pendingChallenge();
    if (this.form.invalid || this.submitting() || !challenge) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const { totpCode } = this.form.getRawValue();
    this.authService.verifyMfa(challenge.preAuthToken, totpCode).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.loginFlowState.clear();
        this.router.navigate([roleHomeRoute(response.role)]);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(mapMfaError(error.status));
      },
    });
  }
}

function mapMfaError(status: number): string {
  switch (status) {
    case 401:
      return 'Nieprawidłowy lub wygasły kod. Spróbuj ponownie.';
    case 423:
      return 'Konto zostało tymczasowo zablokowane po zbyt wielu nieudanych próbach.';
    case 403:
      return 'To konto zostało dezaktywowane.';
    default:
      return 'Wystąpił błąd. Spróbuj ponownie.';
  }
}
