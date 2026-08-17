import type { StaffRole } from '../../../core/auth/auth-state';

/** Mirrors backend AccountResponse (T077, contracts/auth-api.yaml). */
export interface StaffAccountSummary {
  id: string;
  email: string;
  role: StaffRole;
  status: 'ACTIVE' | 'DEACTIVATED';
  mfaEnrolled: boolean;
  createdAt: string;
}
