import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  PatientDetail,
  PatientSummary,
  PatientWriteRequest,
  ToothStateEntry,
  ToothStatus,
} from './patients.models';

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

  getToothChart(patientId: string): Observable<ToothStateEntry[]> {
    return this.http.get<ToothStateEntry[]>(`/patients/${patientId}/tooth-chart`);
  }

  setToothStatus(
    patientId: string,
    toothNumber: number,
    status: ToothStatus,
  ): Observable<ToothStateEntry> {
    return this.http.patch<ToothStateEntry>(`/patients/${patientId}/tooth-chart/${toothNumber}`, {
      status,
    });
  }

  /** FR-004 — always an empty array in this version (placeholder for a future visits module). */
  getVisitHistory(patientId: string): Observable<unknown[]> {
    return this.http.get<unknown[]>(`/patients/${patientId}/visit-history`);
  }
}
