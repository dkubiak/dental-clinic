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
