import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import type { StaffRole } from '../../../core/auth/auth-state';
import { StaffAccountSummary } from './accounts.models';

/**
 * T079 — calls the {@code /accounts} endpoints (T077/T077a, contracts/auth-api.yaml), admin-only
 * per FR-009. Every method here is a thin relay — the backend, not this service, is what actually
 * authorizes and validates each action (plan.md — server is the sole enforcement point).
 */
@Injectable({ providedIn: 'root' })
export class AccountAdminService {
  private readonly http = inject(HttpClient);

  list(): Observable<StaffAccountSummary[]> {
    return this.http.get<StaffAccountSummary[]>('/accounts');
  }

  create(email: string, role: StaffRole): Observable<StaffAccountSummary> {
    return this.http.post<StaffAccountSummary>('/accounts', { email, role });
  }

  changeRole(id: string, role: StaffRole): Observable<StaffAccountSummary> {
    return this.http.patch<StaffAccountSummary>(`/accounts/${id}`, { role });
  }

  deactivate(id: string): Observable<void> {
    return this.http.post<void>(`/accounts/${id}/deactivate`, {});
  }

  reactivate(id: string): Observable<void> {
    return this.http.post<void>(`/accounts/${id}/reactivate`, {});
  }

  /** FR-015b — admin-assisted MFA reset for a lost device (T079a). */
  resetMfa(id: string): Observable<void> {
    return this.http.post<void>(`/accounts/${id}/mfa-reset`, {});
  }
}
