import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

/**
 * T051 — self-service password reset, step 2: consume the reset token and set a new password
 * (FR-016). The token arrives as a {@code ?token=} query param, matching the link
 * PasswordResetService emails (see backend PasswordResetService#requestReset).
 */
@Component({
  selector: 'app-password-reset-confirm',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="reset-page">
      <mat-card class="reset-card">
        <mat-card-header>
          <mat-card-title>Ustaw nowe hasło</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (succeeded()) {
            <p>Hasło zostało zmienione. Możesz się teraz zalogować.</p>
            <a mat-flat-button color="primary" routerLink="/login">Przejdź do logowania</a>
          } @else {
            <form [formGroup]="form" (ngSubmit)="submit()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Nowe hasło (min. 12 znaków)</mat-label>
                <input
                  matInput
                  type="password"
                  formControlName="newPassword"
                  autocomplete="new-password"
                />
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Powtórz nowe hasło</mat-label>
                <input
                  matInput
                  type="password"
                  formControlName="confirmPassword"
                  autocomplete="new-password"
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
                  Ustaw nowe hasło
                }
              </button>
            </form>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .reset-page {
      display: flex;
      justify-content: center;
      padding: 16px;
    }
    .reset-card {
      width: 100%;
      max-width: 420px;
    }
    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }
    .error {
      color: var(--mat-sys-error);
      margin: 0 0 12px;
    }
  `,
})
export class PasswordResetConfirmComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private token: string | null = null;

  readonly submitting = signal(false);
  readonly succeeded = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(12)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator },
  );

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    if (!this.token) {
      this.router.navigate(['/password-reset/request']);
    }
  }

  submit(): void {
    if (this.form.invalid || this.submitting() || !this.token) {
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);

    const { newPassword } = this.form.getRawValue();
    this.http.post('/auth/password-reset/confirm', { token: this.token, newPassword }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.succeeded.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        this.errorMessage.set(mapConfirmError(error.status));
      },
    });
  }
}

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.get('newPassword')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return newPassword && confirmPassword && newPassword !== confirmPassword
    ? { passwordsMismatch: true }
    : null;
}

function mapConfirmError(status: number): string {
  switch (status) {
    case 410:
      return 'Link resetujący wygasł lub został już użyty. Poproś o nowy.';
    case 400:
      return 'Hasło nie spełnia wymagań bezpieczeństwa (min. 12 znaków, brak na liście znanych wyciekłych haseł).';
    default:
      return 'Wystąpił błąd. Spróbuj ponownie.';
  }
}
