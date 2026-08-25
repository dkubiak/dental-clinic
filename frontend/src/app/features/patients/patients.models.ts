/** Mirrors backend PatientSummaryResponse (contracts/patient-api.yaml). */
export interface PatientSummary {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  pesel: string | null;
}

/** Mirrors backend PatientDetailResponse. */
export interface PatientDetail extends PatientSummary {
  addressStreet: string;
  addressBuildingNo: string;
  addressPostalCode: string;
  addressCity: string;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors backend PatientCreateRequest (also used for PATCH — same shape, contracts/patient-api.yaml). */
export interface PatientWriteRequest {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  pesel: string | null;
  addressStreet: string;
  addressBuildingNo: string;
  addressPostalCode: string;
  addressCity: string;
}

/** Binary tooth state (FR-006, spec.md Assumptions — colors/disease codes deferred). */
export type ToothStatus = 'HEALTHY' | 'SICK';

/** Mirrors backend ToothStateResponse (contracts/patient-api.yaml ToothState schema). */
export interface ToothStateEntry {
  toothNumber: number;
  status: ToothStatus;
  updatedAt: string | null;
}
