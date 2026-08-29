import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AllergyCreateRequest, AllergyEntry } from '../patients.models';

/**
 * Calls the {@code /patients/{id}/{allergies,medications,chronic-conditions}[/history]} endpoints
 * (contracts/patient-api.yaml, feature 004). Thin relay, same pattern as {@code PatientsService}
 * (research.md #7) — kept as a separate file rather than folded into {@code PatientsService} to
 * avoid that file growing to ~15 unrelated methods.
 */
@Injectable({ providedIn: 'root' })
export class MedicalHistoryService {
  private readonly http = inject(HttpClient);

  getAllergies(patientId: string): Observable<AllergyEntry[]> {
    return this.http.get<AllergyEntry[]>(`/patients/${patientId}/allergies`);
  }

  getAllergyHistory(patientId: string): Observable<AllergyEntry[]> {
    return this.http.get<AllergyEntry[]>(`/patients/${patientId}/allergies/history`);
  }

  addAllergy(patientId: string, request: AllergyCreateRequest): Observable<AllergyEntry> {
    return this.http.post<AllergyEntry>(`/patients/${patientId}/allergies`, request);
  }
}
