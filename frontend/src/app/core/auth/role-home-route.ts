import { StaffRole } from './auth-state';

/** Role-appropriate home screen after successful MFA (T053, SC-001: <10s end to end). */
export function roleHomeRoute(role: StaffRole): string {
  switch (role) {
    case 'RECEPTION':
      return '/reception';
    case 'DOCTOR':
      return '/doctor';
    case 'ADMINISTRATOR':
      return '/admin';
  }
}
