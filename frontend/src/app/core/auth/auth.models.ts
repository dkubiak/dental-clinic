import type { StaffRole } from './auth-state';

/** Mirrors backend LoginResponse (contracts/auth-api.yaml, extended per LoginResult javadoc). */
export interface LoginResponse {
  preAuthToken: string;
  mfaRequired: boolean;
  /** Present only on an account's first login — not yet MFA-enrolled (data-model.md). */
  mfaSecret: string | null;
  mfaOtpAuthUri: string | null;
}

/** Mirrors backend MfaVerifyResponse. */
export interface MfaVerifyResponse {
  role: StaffRole;
}
