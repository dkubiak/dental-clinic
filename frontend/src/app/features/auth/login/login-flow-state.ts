import { Injectable, signal } from '@angular/core';

export interface PendingMfaChallenge {
  preAuthToken: string;
  /** Set only on an account's first login (not yet MFA-enrolled) — for the setup step. */
  mfaSecret: string | null;
  mfaOtpAuthUri: string | null;
}

/**
 * Transient, in-memory handoff of the pre-auth token (and first-login enrollment data, if any)
 * from LoginComponent (password step) to MfaChallengeComponent (MFA step) — never persisted, and
 * cleared once consumed, since the pre-auth token is a short-lived bearer secret (FR-015a).
 */
@Injectable({ providedIn: 'root' })
export class LoginFlowState {
  private readonly pendingChallenge = signal<PendingMfaChallenge | null>(null);

  readonly current = this.pendingChallenge.asReadonly();

  start(challenge: PendingMfaChallenge): void {
    this.pendingChallenge.set(challenge);
  }

  clear(): void {
    this.pendingChallenge.set(null);
  }
}
