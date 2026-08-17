import { Component, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Minimal role-appropriate landing screen (T053) — proves the post-MFA redirect works and gives
 * staff a logged-in home. The actual reception/doctor feature screens are out of scope for this
 * feature (RBAC/auth foundation only, plan.md) and are deferred to later features, matching
 * feature 002's UI/UX ownership (see CLAUDE.md). The administrator's home is the one exception —
 * this feature's own US2/US3 screens (audit log, account management) link from here via
 * {@link adminLinks} (bound from app.routes.ts route `data`, `withComponentInputBinding`).
 */
@Component({
  selector: 'app-role-home',
  standalone: true,
  imports: [MatButtonModule, MatToolbarModule, RouterLink],
  template: `
    <mat-toolbar color="primary">Panel — {{ roleLabel() }}</mat-toolbar>
    <div class="content">
      <p>Zalogowano pomyślnie jako: {{ roleLabel() }}.</p>
      @if (adminLinks()) {
        <a mat-stroked-button routerLink="/admin/accounts">Zarządzanie kontami</a>
        <a mat-stroked-button routerLink="/admin/audit-log">Log audytowy</a>
      }
      <button mat-stroked-button type="button" (click)="logout()">Wyloguj</button>
    </div>
  `,
  styles: `
    .content {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      align-items: flex-start;
    }
  `,
})
export class RoleHomeComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly roleLabel = input.required<string>();
  readonly adminLinks = input(false);

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
