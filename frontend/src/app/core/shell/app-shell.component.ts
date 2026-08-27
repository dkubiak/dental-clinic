import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from '../auth/auth.service';
import { AuthState, StaffRole } from '../auth/auth-state';
import { BrandMarkComponent } from '../../shared/brand-mark/brand-mark.component';

/**
 * Persistent, mobile-first shell (research.md #8, #9; Principle IV) wrapping every authenticated
 * route: a top toolbar + role-aware nav, bottom-nav on phone / expanded toolbar links on
 * desktop/tablet (CSS breakpoint below — no separate mobile/desktop component). Replaces
 * `RoleHomeComponent`'s placeholder body as the routed shell (T016); each role's actual screen
 * renders into the wrapped `<router-outlet>`.
 *
 * <p>"Nowy pacjent" (FR-001) is a global primary action — a FAB on phone, a toolbar button on
 * desktop — persistent across whichever view is active (research.md #9), shown only for
 * RECEPTION/DOCTOR (the only roles FR-001 grants patient-creation to); ASSISTANT has read-only
 * basic-data access (FR-006a) so never sees it.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterLink,
    RouterOutlet,
    MatButtonModule,
    MatIconModule,
    MatToolbarModule,
    BrandMarkComponent,
  ],
  template: `
    <mat-toolbar color="primary" class="toolbar">
      <app-brand-mark class="brand-mark" />
      <span class="title">Kartoteka pacjentów</span>
      <span class="spacer"></span>
      <nav class="nav-desktop">
        @for (link of navLinks(); track link.path) {
          <a mat-button [routerLink]="link.path">{{ link.label }}</a>
        }
        @if (canCreatePatient()) {
          <a mat-stroked-button routerLink="/patients/new" data-testid="new-patient-action">
            <mat-icon>add</mat-icon>
            Nowy pacjent
          </a>
        }
        <button mat-button type="button" (click)="logout()">Wyloguj</button>
      </nav>
    </mat-toolbar>

    <main class="content">
      <router-outlet />
    </main>

    <nav class="nav-mobile">
      @for (link of navLinks(); track link.path) {
        <a mat-button [routerLink]="link.path">{{ link.label }}</a>
      }
      <button mat-button type="button" (click)="logout()">Wyloguj</button>
    </nav>

    @if (canCreatePatient()) {
      <a
        mat-fab
        color="accent"
        class="fab-mobile"
        routerLink="/patients/new"
        data-testid="new-patient-action"
        aria-label="Nowy pacjent"
      >
        <mat-icon>add</mat-icon>
      </a>
    }
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      padding-bottom: 56px; /* room for the mobile bottom nav */
    }

    .brand-mark {
      margin-right: 8px;
    }

    .title {
      font-size: 1.1rem;
    }

    .spacer {
      flex: 1 1 auto;
    }

    .content {
      padding: 16px;
    }

    /* Mobile-first default: bottom nav, no inline toolbar links, FAB visible. */
    .nav-desktop {
      display: none;
    }

    .nav-mobile {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      display: flex;
      justify-content: space-around;
      background: var(--mat-sys-surface);
      border-top: 1px solid var(--mat-sys-outline-variant);
      z-index: 10;
    }

    .fab-mobile {
      position: fixed;
      right: 16px;
      bottom: 72px;
      z-index: 10;
    }

    /* Progressively enhance for tablet/desktop (Principle IV): toolbar-embedded nav replaces
       the bottom nav / floating FAB. */
    @media (min-width: 768px) {
      :host {
        padding-bottom: 0;
      }

      .nav-desktop {
        display: flex;
        align-items: center;
        gap: 4px;
      }

      .nav-mobile,
      .fab-mobile {
        display: none;
      }
    }
  `,
})
export class AppShellComponent {
  private readonly authState = inject(AuthState);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  // Visit-history (US3) and the tooth chart (US2) are tabs within a patient's own detail view
  // (T050/T056), not separate top-level nav destinations — "Pacjenci" (search/list) is the one
  // shared entry point for every patient-facing role.
  private static readonly NAV_LINKS: Record<StaffRole, { path: string; label: string }[]> = {
    RECEPTION: [{ path: '/patients', label: 'Pacjenci' }],
    DOCTOR: [{ path: '/patients', label: 'Pacjenci' }],
    ASSISTANT: [{ path: '/patients', label: 'Pacjenci' }],
    ADMINISTRATOR: [],
  };

  // RECEPTION/DOCTOR only — FR-001 (create), matches rbac-policy.md; ASSISTANT is read-only.
  private static readonly CAN_CREATE_PATIENT: ReadonlySet<StaffRole> = new Set([
    'RECEPTION',
    'DOCTOR',
  ]);

  readonly navLinks = computed(() => {
    const role = this.authState.currentRole();
    return role ? AppShellComponent.NAV_LINKS[role] : [];
  });

  readonly canCreatePatient = computed(() => {
    const role = this.authState.currentRole();
    return role !== null && AppShellComponent.CAN_CREATE_PATIENT.has(role);
  });

  logout(): void {
    this.authService.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
