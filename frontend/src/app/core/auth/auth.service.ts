import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AuthState } from './auth-state';
import { LoginResponse, MfaVerifyResponse } from './auth.models';

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
}
