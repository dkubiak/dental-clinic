/** Mirrors backend AuditLogEntryDto (T063, contracts/auth-api.yaml). */
export interface AuditLogEntry {
  id: number;
  eventType: string;
  actorAccountId: string | null;
  targetAccountId: string | null;
  occurredAt: string;
  beforeState: string | null;
  afterState: string | null;
  metadata: string | null;
}

/** Mirrors backend AuditLogPageResponse. */
export interface AuditLogPage {
  entries: AuditLogEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditLogFilters {
  from?: string;
  to?: string;
  eventType?: string;
  page?: number;
}
