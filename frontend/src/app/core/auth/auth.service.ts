import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { AuthState } from './auth-state';
import { LoginResponse, MfaVerifyResponse, SessionInfoResponse } from './auth.models';

/**
 * T052 — calls the three login-flow endpoints (contracts/auth-api.yaml). This is UX convenience
 * only: the backend is the sole authorization enforcement point (plan.md), so this service never
 * makes an authorization decision itself — it just relays requests and reflects the resulting
 * role into {@link AuthState} for route-guard/nav purposes (T031/role.guard.ts).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly authState = inject(AuthState);

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/auth/login', { email, password });
  }

  verifyMfa(preAuthToken: string, totpCode: string): Observable<MfaVerifyResponse> {
    return this.http
      .post<MfaVerifyResponse>('/auth/mfa/verify', { preAuthToken, totpCode })
      .pipe(tap((response) => this.authState.setRole(response.role)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>('/auth/logout', {})
      .pipe(tap(() => this.authState.setRole(null)));
  }

  /**
   * Rehydrates {@link AuthState} from a still-valid session cookie — called once at app bootstrap
   * (app.config.ts) so a full page reload/deep link doesn't lose the in-memory role {@link
   * verifyMfa} set (discovered as a live gap during 002-patient-records' quickstart validation,
   * T063). No session (401) is an entirely normal "not logged in" outcome, not an error — swallowed
   * rather than propagated, leaving {@link AuthState.currentRole} at its default {@code null}.
   */
  rehydrateSession(): Observable<void> {
    return this.http.get<SessionInfoResponse>('/auth/session').pipe(
      tap((response) => this.authState.setRole(response.role)),
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }
}
