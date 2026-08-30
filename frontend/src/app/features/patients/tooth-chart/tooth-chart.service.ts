import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, Subject, catchError, throwError } from 'rxjs';
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

/** FR-070/SC-010 — thrown (in place of the raw HttpErrorResponse) by every write method below
 * when the server responds 409, so callers can distinguish a stale-write conflict from any other
 * failure without re-inspecting the HTTP status themselves. */
export class ToothChartConflictError extends Error {
  readonly conflict = true as const;
}

const CONFLICT_MESSAGE =
  'Ktoś inny zmienił ten wpis w międzyczasie. Przeładuj dane, aby zobaczyć aktualny stan.';

/**
 * Calls the {@code /patients/{id}/tooth-chart} and {@code /diagnosis-catalog} endpoints
 * (contracts/patient-api.yaml, feature 005). Every method here is a thin relay — patient-service,
 * not this service, is what actually authorizes and validates each action (mirrors
 * patients.service.ts's own thin-relay pattern).
 */
@Injectable({ providedIn: 'root' })
export class ToothChartService {
  private readonly http = inject(HttpClient);

  /** FR-070/SC-010 — emits a readable Polish message whenever ANY write method below hits a 409,
   * so tooth-chart.component.ts can show a single reload prompt regardless of which child
   * component (detail panel, context menu, …) triggered the write — a conflict is never a silent
   * failure. */
  readonly conflict$ = new Subject<string>();

  /** FR-070/SC-010 — the shared 409-conflict handler every write method below routes through. */
  private handleWriteErrors<T>(source: Observable<T>): Observable<T> {
    return source.pipe(
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.conflict$.next(CONFLICT_MESSAGE);
          return throwError(() => new ToothChartConflictError(CONFLICT_MESSAGE));
        }
        return throwError(() => error);
      }),
    );
  }

  getChart(patientId: string): Observable<ToothChart> {
    return this.http.get<ToothChart>(`/patients/${patientId}/tooth-chart`);
  }

  getPositionHistory(patientId: string, fdiNumber: number): Observable<ToothFinding[]> {
    return this.http.get<ToothFinding[]>(
      `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/history`,
    );
  }

  changeDentitionMode(patientId: string, request: DentitionModePatchRequest): Observable<ToothChart> {
    return this.handleWriteErrors(
      this.http.patch<ToothChart>(`/patients/${patientId}/tooth-chart/dentition-mode`, request),
    );
  }

  changePresence(
    patientId: string,
    fdiNumber: number,
    request: PositionPresencePatchRequest,
  ): Observable<ToothPosition> {
    return this.handleWriteErrors(
      this.http.patch<ToothPosition>(
        `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/presence`,
        request,
      ),
    );
  }

  addCanal(patientId: string, fdiNumber: number, request: RootCanalCreateRequest): Observable<RootCanal> {
    return this.handleWriteErrors(
      this.http.post<RootCanal>(
        `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/canals`,
        request,
      ),
    );
  }

  updateCanal(
    patientId: string,
    fdiNumber: number,
    canalId: string,
    request: RootCanalPatchRequest,
  ): Observable<RootCanal> {
    return this.handleWriteErrors(
      this.http.patch<RootCanal>(
        `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/canals/${canalId}`,
        request,
      ),
    );
  }

  removeCanal(patientId: string, fdiNumber: number, canalId: string): Observable<void> {
    return this.handleWriteErrors(
      this.http.delete<void>(
        `/patients/${patientId}/tooth-chart/positions/${fdiNumber}/canals/${canalId}`,
      ),
    );
  }

  addFinding(patientId: string, request: ToothFindingCreateRequest): Observable<ToothFinding> {
    return this.handleWriteErrors(
      this.http.post<ToothFinding>(`/patients/${patientId}/tooth-chart/findings`, request),
    );
  }

  addFindingsBulk(
    patientId: string,
    request: ToothFindingBulkCreateRequest,
  ): Observable<ToothFindingBulkResult> {
    return this.handleWriteErrors(
      this.http.post<ToothFindingBulkResult>(
        `/patients/${patientId}/tooth-chart/findings/bulk`,
        request,
      ),
    );
  }

  closeFinding(patientId: string, findingId: string, request: FindingCloseRequest): Observable<ToothFinding> {
    return this.handleWriteErrors(
      this.http.post<ToothFinding>(
        `/patients/${patientId}/tooth-chart/findings/${findingId}/close`,
        request,
      ),
    );
  }

  correctFinding(
    patientId: string,
    findingId: string,
    request: ToothFindingCreateRequest,
  ): Observable<ToothFinding> {
    return this.handleWriteErrors(
      this.http.post<ToothFinding>(
        `/patients/${patientId}/tooth-chart/findings/${findingId}/correct`,
        request,
      ),
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
