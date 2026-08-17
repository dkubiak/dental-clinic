import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditLogFilters, AuditLogPage } from './audit-log.models';

/** T065 — calls {@code GET /audit-log} (contracts/auth-api.yaml), admin-only per FR-008a. */
@Injectable({ providedIn: 'root' })
export class AuditLogService {
  private readonly http = inject(HttpClient);

  list(filters: AuditLogFilters = {}): Observable<AuditLogPage> {
    let params = new HttpParams();
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    if (filters.eventType) {
      params = params.set('eventType', filters.eventType);
    }
    if (filters.page != null) {
      params = params.set('page', filters.page);
    }
    return this.http.get<AuditLogPage>('/audit-log', { params });
  }
}
