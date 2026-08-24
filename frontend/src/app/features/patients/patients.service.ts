import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PatientDetail, PatientSummary, PatientWriteRequest } from './patients.models';

/**
 * Calls the {@code /patients} endpoints (contracts/patient-api.yaml, US1). Every method here is a
 * thin relay — patient-service, not this service, is what actually authorizes and validates each
 * action (plan.md — server is the sole enforcement point).
 */
@Injectable({ providedIn: 'root' })
export class PatientsService {
  private readonly http = inject(HttpClient);

  search(q: string): Observable<PatientSummary[]> {
    return this.http.get<PatientSummary[]>('/patients', { params: new HttpParams().set('q', q) });
  }

  get(id: string): Observable<PatientDetail> {
    return this.http.get<PatientDetail>(`/patients/${id}`);
  }

  create(request: PatientWriteRequest): Observable<PatientDetail> {
    return this.http.post<PatientDetail>('/patients', request);
  }

  update(id: string, request: PatientWriteRequest): Observable<PatientDetail> {
    return this.http.patch<PatientDetail>(`/patients/${id}`, request);
  }
}
