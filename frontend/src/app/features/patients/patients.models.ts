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

/** Feature 005 — replaces the binary ToothStatus/ToothStateEntry model outright (research.md D1). */
export type DentitionMode = 'PERMANENT' | 'DECIDUOUS' | 'MIXED';
export type DentitionType = 'PERMANENT' | 'DECIDUOUS';
export type ToothType = 'INCISOR' | 'CANINE' | 'PREMOLAR' | 'MOLAR';
export type ToothPresence = 'PRESENT' | 'EXTRACTED' | 'CONGENITALLY_MISSING' | 'UNERUPTED';
export type RootCanalState = 'NEEDS_TREATMENT' | 'TREATED' | 'UNDERTREATED';
export type DiagnosisCategory =
  | 'HARD_TISSUE'
  | 'PULP_PERIAPICAL'
  | 'TRAUMA'
  | 'NON_CARIOUS_LESION'
  | 'PERIODONTAL_SOFT_TISSUE'
  | 'ERUPTION_MISSING'
  | 'POST_TREATMENT_RESTORATION';
export type AnatomicalScope = 'SURFACE' | 'WHOLE_TOOTH' | 'ROOT_PERIAPICAL' | 'PERIODONTIUM';
export type FindingLayer = 'DIAGNOSIS' | 'EXISTING_STATE';
export type ToothSurface = 'MESIAL' | 'DISTAL' | 'VESTIBULAR' | 'LINGUAL_PALATAL' | 'OCCLUSAL_INCISAL';
export type FindingClinicalStatus = 'ACTIVE' | 'RESOLVED';
export type FindingRecordStatus = 'CURRENT' | 'SUPERSEDED';
export type FindingAuthorRole = 'DOCTOR' | 'ASSISTANT';

/** Mirrors backend RootCanalResponse (contracts/patient-api.yaml RootCanal schema). */
export interface RootCanal {
  id: string;
  name: string;
  state: RootCanalState;
  removed: boolean;
  version: number;
}

/** Mirrors backend DiagnosisCatalogEntryResponse (contracts/patient-api.yaml DiagnosisCatalogEntry schema). */
export interface DiagnosisCatalogEntry {
  id: string;
  code: string;
  namePl: string;
  category: DiagnosisCategory;
  anatomicalScope: AnatomicalScope;
  layer: FindingLayer;
  icd10Code: string | null;
  severityOptions: string[] | null;
  allowedForMissingTooth: boolean;
  deciduousAllowed: boolean;
  quickAccess: boolean;
  requiresFreeText: boolean;
}

/** Mirrors backend ToothFindingResponse (contracts/patient-api.yaml ToothFinding schema). */
export interface ToothFinding {
  id: string;
  fdiNumber: number;
  diagnosisCatalogEntry: DiagnosisCatalogEntry;
  surfaces: ToothSurface[] | null;
  rootCanalId: string | null;
  severity: string | null;
  freeTextDescription: string | null;
  note: string | null;
  diagnosisDate: string;
  resolvedDate: string | null;
  clinicalStatus: FindingClinicalStatus;
  recordStatus: FindingRecordStatus;
  supersedesFindingId: string | null;
  authorAccountId: string;
  authorRole: FindingAuthorRole;
  createdAt: string;
}

/** Mirrors backend ToothPositionResponse (contracts/patient-api.yaml ToothPosition schema). */
export interface ToothPosition {
  fdiNumber: number;
  dentitionType: DentitionType;
  toothType: ToothType;
  presence: ToothPresence;
  presenceDate: string | null;
  version: number;
  canals: RootCanal[];
  currentFindings: ToothFinding[];
}

/** Mirrors backend ToothChartResponse (contracts/patient-api.yaml ToothChart schema). */
export interface ToothChart {
  patientId: string;
  dentitionMode: DentitionMode;
  positions: ToothPosition[];
}

/** Mirrors backend ToothFindingCreateRequest (contracts/patient-api.yaml schema) — used for both
 * create and correct (FR-033, the `.../correct` endpoint takes the same body shape; `fdiNumber` is
 * ignored there since the target position is fixed to the finding being corrected). */
export interface ToothFindingCreateRequest {
  fdiNumber: number;
  diagnosisCatalogEntryId: string;
  surfaces?: ToothSurface[] | null;
  rootCanalId?: string | null;
  severity?: string | null;
  freeTextDescription?: string | null;
  note?: string | null;
  diagnosisDate: string;
}

/** Mirrors backend ToothFindingBulkCreateRequest (FR-004a-c, US6). */
export interface ToothFindingBulkCreateRequest {
  fdiNumbers: number[];
  diagnosisCatalogEntryId: string;
  surfaces?: ToothSurface[] | null;
  severity?: string | null;
  freeTextDescription?: string | null;
  note?: string | null;
  diagnosisDate: string;
}

/** Mirrors backend ToothFindingBulkResult. */
export interface ToothFindingBulkResult {
  created: ToothFinding[];
  skipped: Array<{ fdiNumber: number; reason: string }>;
}

/** Body for {@code POST .../findings/{id}/close} (FR-032). */
export interface FindingCloseRequest {
  resolvedDate: string;
  note?: string | null;
}

/** Body for {@code PATCH .../tooth-chart/dentition-mode} (FR-044/FR-045). */
export interface DentitionModePatchRequest {
  dentitionMode: DentitionMode;
}

/** Body for {@code PATCH .../positions/{fdi}/presence} — {@code expectedVersion} is the
 * optimistic-concurrency token echoed back from the last read (research.md D7, FR-070). */
export interface PositionPresencePatchRequest {
  presence: ToothPresence;
  presenceDate?: string | null;
  expectedVersion: number;
}

/** Body for {@code POST .../positions/{fdi}/canals} (FR-065). */
export interface RootCanalCreateRequest {
  name: string;
}

/** Body for {@code PATCH .../positions/{fdi}/canals/{canalId}} (FR-065/FR-066). */
export interface RootCanalPatchRequest {
  name?: string | null;
  state?: RootCanalState | null;
  expectedVersion: number;
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

/** Mirrors backend MedicationEntryResponse (contracts/patient-api.yaml MedicationEntry schema). */
export interface MedicationEntry {
  id: string;
  name: string;
  dosage: string;
  startDate: string;
  recordStatus: RecordStatus;
  supersedesEntryId: string | null;
  createdAt: string;
}

/** Mirrors backend MedicationCreateRequest (contracts/patient-api.yaml MedicationCreateRequest schema). */
export interface MedicationCreateRequest {
  name: string;
  dosage: string;
  startDate: string;
  supersedesEntryId?: string | null;
}

export type ChronicConditionStatus = 'ACTIVE' | 'PAST';

/** Mirrors backend ChronicConditionEntryResponse (contracts/patient-api.yaml ChronicConditionEntry schema). */
export interface ChronicConditionEntry {
  id: string;
  name: string;
  clinicalStatus: ChronicConditionStatus;
  diagnosisDate: string;
  recordStatus: RecordStatus;
  supersedesEntryId: string | null;
  createdAt: string;
}

/** Mirrors backend ChronicConditionCreateRequest (contracts/patient-api.yaml ChronicConditionCreateRequest schema). */
export interface ChronicConditionCreateRequest {
  name: string;
  clinicalStatus: ChronicConditionStatus;
  diagnosisDate: string;
  supersedesEntryId?: string | null;
}
