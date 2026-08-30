import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { DiagnosisCatalogEntry } from '../patients.models';
import { ToothChartService } from './tooth-chart.service';

const RECENT_CODES_STORAGE_KEY = 'pu.tooth-chart.recent-diagnosis-codes';
const MAX_RECENT_CODES = 8;

/**
 * FR-013/FR-020/research.md D12 — wraps catalog search and maintains a client-side "ostatnio
 * używane" cache, purely a per-browser UX convenience (no new backend table/endpoint/audit
 * surface, per D12's own rationale — it rides the same audited POST .../findings call every save
 * already makes).
 *
 * D12 specifies the cache is keyed by the signed-in clinician's account id; the frontend does not
 * currently expose an account id anywhere (AuthState only carries the role, core/auth/auth-state.ts)
 * — until that changes, this uses a single per-browser key rather than inventing new auth plumbing
 * out of this feature's scope. Revisit once account id becomes available client-side.
 */
@Injectable({ providedIn: 'root' })
export class DiagnosisCatalogService {
  private readonly toothChartService = inject(ToothChartService);

  search(query?: string, quickAccessOnly?: boolean): Observable<DiagnosisCatalogEntry[]> {
    return this.toothChartService.searchDiagnosisCatalog(query, quickAccessOnly);
  }

  /** FR-020a — recorded on every successful save via {@link recordUsed}. */
  recentCodes(): string[] {
    try {
      const raw = localStorage.getItem(RECENT_CODES_STORAGE_KEY);
      return raw ? (JSON.parse(raw) as string[]) : [];
    } catch {
      return [];
    }
  }

  recordUsed(code: string): void {
    const existing = this.recentCodes().filter((c) => c !== code);
    const updated = [code, ...existing].slice(0, MAX_RECENT_CODES);
    try {
      localStorage.setItem(RECENT_CODES_STORAGE_KEY, JSON.stringify(updated));
    } catch {
      // best-effort convenience cache — a full/unavailable localStorage must not break saving
    }
  }

  /** Convenience for the quick context-menu's "ostatnio używane" section. */
  recentEntries(allEntries: DiagnosisCatalogEntry[]): DiagnosisCatalogEntry[] {
    const codes = this.recentCodes();
    const byCode = new Map(allEntries.map((e) => [e.code, e]));
    return codes.map((code) => byCode.get(code)).filter((e): e is DiagnosisCatalogEntry => !!e);
  }

  /** Wraps a save call so a successful save automatically updates the recency cache. */
  withRecencyTracking<T>(code: string, save$: Observable<T>): Observable<T> {
    return save$.pipe(tap(() => this.recordUsed(code)));
  }
}
