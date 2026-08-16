import { Injectable, signal } from '@angular/core';

export type StaffRole = 'RECEPTION' | 'DOCTOR' | 'ADMINISTRATOR';

/**
 * Holds the currently authenticated user's role for UX purposes only (route guards, nav
 * visibility) — never the authorization source of truth (core/rbac note, plan.md). Populated by
 * the full AuthService once it exists (T052, Phase 3) after a successful login/MFA verification.
 */
@Injectable({ providedIn: 'root' })
export class AuthState {
  readonly currentRole = signal<StaffRole | null>(null);

  setRole(role: StaffRole | null): void {
    this.currentRole.set(role);
  }
}
