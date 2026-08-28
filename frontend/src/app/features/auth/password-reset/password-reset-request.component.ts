import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ThemeToggleComponent } from '../../../core/theme/theme-toggle.component';

/**
 * T051 — self-service password reset, step 1: request a reset link (FR-016). The backend always
 * responds 202 regardless of whether the email matches an account (FR-005/FR-016), so this screen
 * shows the same confirmation either way — it has no way to distinguish the two cases, by design.
 */
@Component({
  selector: 'app-password-reset-request',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ThemeToggleComponent,
  ],
  template: `
    <div class="reset-page">
      <app-theme-toggle class="theme-toggle" />
      <mat-card class="reset-card">
        <mat-card-header>
          <mat-card-title>Reset hasła</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (submitted()) {
            <p>
              Jeśli podany adres e-mail jest powiązany z kontem, wysłaliśmy na niego link do
              resetu hasła. Link jest ważny przez ograniczony czas.
            </p>
            <a mat-stroked-button routerLink="/login">Powrót do logowania</a>
          } @else {
            <form [formGroup]="form" (ngSubmit)="submit()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Służbowy adres e-mail</mat-label>
                <input matInput type="email" formControlName="email" autocomplete="username" />
              </mat-form-field>

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
                  Wyślij link resetujący
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
    .reset-card {
      width: 100%;
      max-width: 420px;
    }
    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }
  `,
})
export class PasswordResetRequestComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly http = inject(HttpClient);

  readonly submitting = signal(false);
  readonly submitted = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);

    const { email } = this.form.getRawValue();
    this.http.post('/auth/password-reset/request', { email }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.submitted.set(true);
      },
      // Uniform outcome regardless of server response (FR-005/FR-016) — even a network hiccup
      // shows the same confirmation rather than leaking whether the email matched an account.
      error: () => {
        this.submitting.set(false);
        this.submitted.set(true);
      },
    });
  }
}
