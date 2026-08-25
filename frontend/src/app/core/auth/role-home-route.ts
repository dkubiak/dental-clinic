import { StaffRole } from './auth-state';

/** Role-appropriate home screen after successful MFA (T053, SC-001: <10s end to end). Patient
 * search is the shared default landing view for RECEPTION/DOCTOR/ASSISTANT (T040, US1) — there is
 * no per-role home screen for them anymore. */
export function roleHomeRoute(role: StaffRole): string {
  switch (role) {
    case 'RECEPTION':
    case 'DOCTOR':
    case 'ASSISTANT':
      return '/patients';
    case 'ADMINISTRATOR':
      return '/admin';
  }
}
