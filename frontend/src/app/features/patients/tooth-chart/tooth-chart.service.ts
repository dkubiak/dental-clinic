import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  DentitionModePatchRequest,
  DiagnosisCatalogEntry,
  FindingCloseRequest,
  PositionPresencePatchRequest,
  RootCanal,
  RootCanalCreateRequest,
  RootCanalPatchRequest,
  ToothChart,
  ToothFinding,
  ToothFindingBulkCreateRequest,
  ToothFindingBulkResult,
  ToothFindingCreateRequest,
  ToothPosition,
} from '../patients.models';

/**
 * Calls the {@code /patients/{id}/tooth-chart} and {@code /diagnosis-catalog} endpoints
 * (contracts/patient-api.yaml, feature 005). Every method here is a thin relay — patient-service,
 * not this service, is what actually authorizes and validates each action (mirrors
 * patients.service.ts's own thin-relay pattern).
 */
@Injectable({ providedIn: 'root' })
export class ToothChartService {
  private readonly http = inject(HttpClient);

  getChart(patientId: string): Observable<ToothChart> {
    return this.http.get<ToothChart>(`/patients/${patientId}/tooth-chart`);
  }

  getPositionHistory(patientId: string, fdiNumber: number): Observable<ToothFinding[]> {
    return this.http.get<ToothFinding[]>(
      `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/history`,
    );
  }

  changeDentitionMode(patientId: string, request: DentitionModePatchRequest): Observable<ToothChart> {
    return this.http.patch<ToothChart>(`/patients/${patientId}/tooth-chart/dentition-mode`, request);
  }

  changePresence(
    patientId: string,
    fdiNumber: number,
    request: PositionPresencePatchRequest,
  ): Observable<ToothPosition> {
    return this.http.patch<ToothPosition>(
      `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/presence`,
      request,
    );
  }

  addCanal(patientId: string, fdiNumber: number, request: RootCanalCreateRequest): Observable<RootCanal> {
    return this.http.post<RootCanal>(
      `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/canals`,
      request,
    );
  }

  updateCanal(
    patientId: string,
    fdiNumber: number,
    canalId: string,
    request: RootCanalPatchRequest,
  ): Observable<RootCanal> {
    return this.http.patch<RootCanal>(
      `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/canals/${canalId}`,
      request,
    );
  }

  removeCanal(patientId: string, fdiNumber: number, canalId: string): Observable<void> {
    return this.http.delete<void>(
      `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/canals/${canalId}`,
    );
  }

  addFinding(patientId: string, request: ToothFindingCreateRequest): Observable<ToothFinding> {
    return this.http.post<ToothFinding>(`/patients/${patientId}/tooth-chart/findings`, request);
  }

  addFindingsBulk(
    patientId: string,
    request: ToothFindingBulkCreateRequest,
  ): Observable<ToothFindingBulkResult> {
    return this.http.post<ToothFindingBulkResult>(
      `/patients/${patientId}/tooth-chart/findings/bulk`,
      request,
    );
  }

  closeFinding(patientId: string, findingId: string, request: FindingCloseRequest): Observable<ToothFinding> {
    return this.http.post<ToothFinding>(
      `/patients/${patientId}/tooth-chart/findings/${findingId}/close`,
      request,
    );
  }

  correctFinding(
    patientId: string,
    findingId: string,
    request: ToothFindingCreateRequest,
  ): Observable<ToothFinding> {
    return this.http.post<ToothFinding>(
      `/patients/${patientId}/tooth-chart/findings/${findingId}/correct`,
      request,
    );
  }

  searchDiagnosisCatalog(query?: string, quickAccessOnly?: boolean): Observable<DiagnosisCatalogEntry[]> {
    let params = new HttpParams();
    if (query) {
      params = params.set('q', query);
    }
    if (quickAccessOnly) {
      params = params.set('quickAccessOnly', 'true');
    }
    return this.http.get<DiagnosisCatalogEntry[]>('/diagnosis-catalog', { params });
  }
}
