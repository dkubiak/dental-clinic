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
  /** Added by feature 004 — fact-only critical-allergy signal, no clinical detail (FR-005). */
  hasCriticalAllergyAlert: boolean;
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

/**
 * Feature 004 — technical correction-lifecycle flag shared by all three medical-history entities
 * (data-model.md), independent of any clinical-status field an entity may also carry.
 */
export type RecordStatus = 'CURRENT' | 'SUPERSEDED';

export type AllergySeverity = 'CRITICAL' | 'MODERATE';

/** Mirrors backend AllergyEntryResponse (contracts/patient-api.yaml AllergyEntry schema). */
export interface AllergyEntry {
  id: string;
  substance: string;
  reactionType: string;
  severity: AllergySeverity;
  recordStatus: RecordStatus;
  supersedesEntryId: string | null;
  createdAt: string;
}

/** Mirrors backend AllergyCreateRequest (contracts/patient-api.yaml AllergyCreateRequest schema). */
export interface AllergyCreateRequest {
  substance: string;
  reactionType: string;
  severity: AllergySeverity;
  supersedesEntryId?: string | null;
}
