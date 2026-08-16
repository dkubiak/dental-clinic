import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthState, StaffRole } from './auth-state';

/**
 * UX redirect only — NOT the authorization source of truth (plan.md Project Structure note; the
 * server enforces via @PreAuthorize + GlobalExceptionHandler's 404-not-403 mapping, T028/T047).
 * This guard exists solely so a receptionist clicking a doctor-only nav link gets redirected
 * instead of seeing a broken page; a determined user bypassing this guard entirely still gets a
 * 404 from the backend, never real data (FR-005).
 */
export function roleGuard(allowedRoles: StaffRole[]): CanMatchFn {
  return () => {
    const authState = inject(AuthState);
    const router = inject(Router);
    const role = authState.currentRole();

    if (role && allowedRoles.includes(role)) {
      return true;
    }
    return router.parseUrl('/login');
  };
}
