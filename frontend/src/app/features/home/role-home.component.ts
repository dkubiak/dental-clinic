import { Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Minimal role-appropriate landing screen (T053) — proves the post-MFA redirect works and gives
 * staff a logged-in home. The actual reception/doctor/admin feature screens are out of scope for
 * this feature (RBAC/auth foundation only, plan.md) and are deferred to later features, matching
 * feature 002's UI/UX ownership (see CLAUDE.md).
 */
@Component({
  selector: 'app-role-home',
  standalone: true,
  imports: [MatButtonModule, MatToolbarModule],
  template: `
    <mat-toolbar color="primary">Panel — {{ roleLabel() }}</mat-toolbar>
    <div class="content">
      <p>Zalogowano pomyślnie jako: {{ roleLabel() }}.</p>
      <button mat-stroked-button type="button" (click)="logout()">Wyloguj</button>
    </div>
  `,
  styles: `
    .content {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
  `,
})
export class RoleHomeComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly roleLabel = input.required<string>();

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
