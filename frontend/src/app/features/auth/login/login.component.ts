import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/auth/auth.service';
import { BrandMarkComponent } from '../../../shared/brand-mark/brand-mark.component';
import { ThemeToggleComponent } from '../../../core/theme/theme-toggle.component';
import { LoginFlowState } from './login-flow-state';

/**
 * T049 — login page, step 1 (email + password). Themed per Projekt Uśmiech (003-brand-ui-theme);
 * the theme toggle is present here too since it must work before authentication (FR-008). On
 * success, hands the pre-auth token off to LoginFlowState and navigates to the MFA challenge step
 * (T050); the password step alone never grants access (FR-015, mandatory MFA).
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    BrandMarkComponent,
    ThemeToggleComponent,
  ],
  template: `
    <div class="login-page">
      <app-theme-toggle class="theme-toggle" />
      <mat-card class="login-card">
        <mat-card-header>
          <app-brand-mark class="brand-mark" />
          <mat-card-title>Logowanie personelu</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Adres e-mail</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="username" />
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Hasło</mat-label>
              <input
                matInput
                type="password"
                formControlName="password"
                autocomplete="current-password"
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
                Zaloguj się
              }
            </button>

            <a class="forgot-password" routerLink="/password-reset/request">
              Nie pamiętasz hasła?
            </a>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .login-page {
      position: relative;
      display: flex;
      justify-content: center;
      padding: 16px;
    }
    .theme-toggle {
      position: absolute;
      top: 16px;
      right: 16px;
      z-index: 1;
    }
    .login-card {
      width: 100%;
      max-width: 420px;
    }
    .brand-mark {
      margin-bottom: 8px;
    }
    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }
    .error {
      color: var(--mat-sys-error);
      margin: 0 0 12px;
    }
    .forgot-password {
      display: block;
      margin-top: 12px;
      text-align: center;
    }
  `,
})
export class LoginComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly loginFlowState = inject(LoginFlowState);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(12)]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();
    this.authService.login(email, password).subscribe({
      next: (response) => {
        this.submitting.set(false);
        this.loginFlowState.start({
          preAuthToken: response.preAuthToken,
          mfaSecret: response.mfaSecret,
          mfaOtpAuthUri: response.mfaOtpAuthUri,
        });
        this.router.navigate(['/login/mfa']);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(mapLoginError(error.status));
      },
    });
  }
}

function mapLoginError(status: number): string {
  switch (status) {
    case 401:
      return 'Nieprawidłowy adres e-mail lub hasło.';
    case 423:
      return 'Konto zostało tymczasowo zablokowane po zbyt wielu nieudanych próbach. Spróbuj ponownie później.';
    case 403:
      return 'To konto zostało dezaktywowane. Skontaktuj się z administratorem.';
    case 429:
      return 'Zbyt wiele prób logowania. Spróbuj ponownie za chwilę.';
    default:
      return 'Wystąpił błąd podczas logowania. Spróbuj ponownie.';
  }
}
