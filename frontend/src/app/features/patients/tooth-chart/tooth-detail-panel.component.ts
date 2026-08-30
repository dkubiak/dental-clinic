import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import {
  AnatomicalScope,
  DiagnosisCatalogEntry,
  DiagnosisCategory,
  RootCanal,
  RootCanalState,
  ToothFinding,
  ToothPosition,
  ToothPresence,
  ToothSurface,
} from '../patients.models';
import { DiagnosisCatalogService } from './diagnosis-catalog.service';
import { ToothChartService } from './tooth-chart.service';
import { SurfaceMapComponent } from './surface-map.component';
import { toothAnatomy } from './tooth-geometry';

type SaveState = 'idle' | 'saving' | 'success' | 'error';

/** Ported from the mockup's `CAT` array (FR-011..FR-021) — Polish category labels, in the
 * approved display order. */
const CATEGORY_LABELS: Record<DiagnosisCategory, string> = {
  HARD_TISSUE: 'Twarde tkanki zęba',
  PULP_PERIAPICAL: 'Miazga i tkanki okołowierzchołkowe',
  TRAUMA: 'Urazy i pęknięcia',
  NON_CARIOUS_LESION: 'Ubytki niepróchnicowego pochodzenia',
  PERIODONTAL_SOFT_TISSUE: 'Przyzębie i tkanki miękkie',
  ERUPTION_MISSING: 'Wyrzynanie i braki zębowe',
  POST_TREATMENT_RESTORATION: 'Stan istniejący i uzupełnienia',
};
const CATEGORY_ORDER: DiagnosisCategory[] = [
  'HARD_TISSUE',
  'PULP_PERIAPICAL',
  'TRAUMA',
  'NON_CARIOUS_LESION',
  'PERIODONTAL_SOFT_TISSUE',
  'ERUPTION_MISSING',
  'POST_TREATMENT_RESTORATION',
];
/** Ported from the mockup's `dxPicker()` scope-tag labels. */
const SCOPE_LABELS: Record<AnatomicalScope, string> = {
  SURFACE: 'powierzchnia',
  WHOLE_TOOTH: 'cały ząb',
  ROOT_PERIAPICAL: 'korzeń/kanał',
  PERIODONTIUM: 'przyzębie',
};

/**
 * US1/US2/US3/US4 — the tooth-detail form: catalog search, severity, note, surface picker (hidden
 * for non-SURFACE-scope entries, FR-023), save wiring, discard/save feedback (FR-055/FR-056/
 * FR-071), and a responsive side-by-side/drawer host (FR-006) — CSS decides the layout, the
 * content is identical and never hidden either way.
 */
@Component({
  selector: 'app-tooth-detail-panel',
  standalone: true,
  imports: [SurfaceMapComponent],
  template: `
    <section class="detail-panel" [attr.aria-label]="'Szczegóły zęba ' + fdiNumber()">
      <header>
        <h3>Ząb {{ fdiNumber() }}</h3>
        <p class="anatomical-name">{{ anatomicalName() }}</p>
        <!-- One instance serves both roles (US1 Scenario 2's "powiększony schemat powierzchni"
             and the SURFACE-scope picker below) — always shows existing surfaces; becomes
             selectable once a SURFACE-scope entry is chosen (canSelectSurfaces()). -->
        <app-surface-map
          [fdiNumber]="fdiNumber()"
          [size]="160"
          [showLabels]="true"
          [existingSurfaces]="existingSurfaces()"
          [selectedSurfaces]="selectedSurfaces()"
          (surfaceToggled)="canSelectSurfaces() && toggleSurface($event)"
        />
      </header>

      <!-- FR-038/FR-039 — presence controls: extracted/congenitally-missing/unerupted are
           rendered distinctly from healthy/diseased on the diagram (tooth-arch.component.ts)
           without relying on color alone; this section is just the write path for that state. -->
      <section class="presence" aria-label="Stan zęba">
        <h4>Stan zęba</h4>
        <div class="presence-options" role="group" aria-label="Stan zęba">
          @for (option of presenceOptions; track option.value) {
            <button
              type="button"
              [attr.data-testid]="'presence-' + option.value"
              [attr.aria-pressed]="position().presence === option.value"
              (click)="setPresence(option.value)"
            >
              {{ option.labelPl }}
            </button>
          }
        </div>
      </section>

      <!-- FR-065/FR-066/FR-068 — root canals: add/rename/change-state/soft-remove, up to 6 per
           position; rendering inside the root silhouette is tooth-arch.component.ts's job. -->
      <section class="canals" aria-label="Kanały korzeniowe" data-testid="canal-section">
        <h4>Kanały korzeniowe</h4>
        <ul>
          @for (canal of nonRemovedCanals(); track canal.id) {
            <li [attr.data-testid]="'canal-' + canal.id">
              <input
                type="text"
                [attr.data-testid]="'canal-name-' + canal.id"
                [attr.aria-label]="'Nazwa kanału ' + canal.name"
                [value]="canal.name"
                (change)="renameCanal(canal, $any($event.target).value)"
              />
              <span class="canal-state-toggle" role="group" [attr.aria-label]="'Stan leczenia kanału ' + canal.name">
                @for (option of canalStateOptions; track option.value) {
                  <button
                    type="button"
                    [attr.data-testid]="'canal-state-' + canal.id + '-' + option.value"
                    [attr.aria-pressed]="canal.state === option.value"
                    (click)="changeCanalState(canal, option.value)"
                  >
                    {{ option.labelPl }}
                  </button>
                }
              </span>
              <button
                type="button"
                [attr.data-testid]="'canal-remove-' + canal.id"
                [attr.aria-label]="'Usuń kanał ' + canal.name"
                (click)="removeCanal(canal)"
              >
                Usuń
              </button>
            </li>
          }
        </ul>
        <div class="add-canal-form" data-testid="add-canal-form">
          <input
            type="text"
            data-testid="new-canal-name-input"
            aria-label="Nazwa nowego kanału"
            placeholder="Nazwa kanału (np. MB, policzkowy bliższy)"
            [value]="newCanalName()"
            (input)="newCanalName.set($any($event.target).value)"
          />
          <button
            type="button"
            data-testid="add-canal-submit"
            [disabled]="!newCanalName().trim() || nonRemovedCanals().length >= 6"
            (click)="submitAddCanal()"
          >
            Dodaj kanał
          </button>
        </div>
      </section>

      <section class="findings" data-testid="finding-list">
        <h4>Wpisy</h4>
        @if (position().currentFindings.length === 0) {
          <p data-testid="finding-list-empty">Brak wpisów dla tego zęba.</p>
        } @else {
          <ul>
            @for (finding of position().currentFindings; track finding.id) {
              <li data-testid="finding-item">
                {{ finding.diagnosisCatalogEntry.namePl }}
                @if (finding.clinicalStatus === 'ACTIVE') {
                  <button
                    type="button"
                    [attr.data-testid]="'correct-finding-' + finding.id"
                    (click)="startCorrect(finding)"
                  >
                    Koryguj
                  </button>
                  <button
                    type="button"
                    [attr.data-testid]="'close-finding-' + finding.id"
                    (click)="startClose(finding.id)"
                  >
                    Zamknij rozpoznanie
                  </button>
                }
              </li>
            }
          </ul>
        }

        <!-- FR-032 — "Zamknij rozpoznanie": routes through the same supersede primitive as a
             correction, but only asks for the date treatment concluded. -->
        @if (closingFindingId(); as closingId) {
          <form class="close-form" data-testid="close-form" (submit)="submitClose($event, closingId)">
            <label>
              Data zamknięcia
              <input
                type="date"
                data-testid="close-resolved-date-input"
                [value]="closeResolvedDate()"
                (input)="closeResolvedDate.set($any($event.target).value)"
              />
            </label>
            <button type="submit" data-testid="close-submit" [disabled]="!closeResolvedDate()">
              Potwierdź zamknięcie
            </button>
            <button type="button" data-testid="close-cancel" (click)="cancelClose()">Anuluj</button>
          </form>
        }

        <!-- FR-034 — resolved/superseded entries stay out of the default list above and are
             only fetched/shown once the clinician explicitly asks for them. -->
        <button
          type="button"
          data-testid="history-toggle"
          [attr.aria-expanded]="historyOpen()"
          (click)="toggleHistory()"
        >
          Historia zęba
        </button>
        @if (historyOpen()) {
          @if (history(); as h) {
            @if (h.length === 0) {
              <p data-testid="history-empty">Brak wcześniejszych wpisów.</p>
            } @else {
              <ul data-testid="history-list">
                @for (item of h; track item.id) {
                  <li data-testid="history-item">
                    {{ item.diagnosisCatalogEntry.namePl }} — {{ item.recordStatus }} /
                    {{ item.clinicalStatus }}
                  </li>
                }
              </ul>
            }
          } @else {
            <p data-testid="history-loading">Wczytywanie historii…</p>
          }
        }
      </section>

      <form (submit)="save($event)">
        @if (correctingFindingId(); as correctingId) {
          <p data-testid="correcting-notice">
            Korygujesz wpis. <button type="button" data-testid="cancel-correct" (click)="cancelCorrect()">Anuluj korektę</button>
          </p>
        }
        <label>
          Rozpoznanie
          <input
            type="text"
            data-testid="catalog-search-input"
            [value]="query()"
            (input)="onQueryChange($any($event.target).value)"
          />
        </label>

        @if (searchResults().length > 0) {
          <ul class="search-results" data-testid="catalog-search-results">
            @for (group of groupedSearchResults(); track group.category) {
              <li class="catalog-group-label">{{ group.label }}</li>
              @for (entry of group.entries; track entry.id) {
                <li>
                  <button
                    type="button"
                    class="catalog-entry"
                    [attr.data-testid]="'catalog-entry-' + entry.code"
                    (click)="selectEntry(entry)"
                  >
                    <span class="layer-dot" [class.layer-dot-existing]="entry.layer === 'EXISTING_STATE'"></span>
                    <span class="catalog-entry-name">{{ entry.namePl }}</span>
                    <span class="catalog-entry-scope">{{ scopeLabel(entry.anatomicalScope) }}</span>
                    <span class="catalog-entry-icd">{{ entry.icd10Code ?? '—' }}</span>
                  </button>
                </li>
              }
            }
          </ul>
        }

        @if (selectedEntry(); as entry) {
          <p data-testid="selected-entry">{{ entry.namePl }}</p>

          @if (entry.anatomicalScope === 'SURFACE') {
            <p data-testid="surface-picker">Wskaż powierzchnię na schemacie powyżej.</p>
          }

          @if (entry.severityOptions; as options) {
            <label>
              Nasilenie
              <select
                data-testid="severity-select"
                [value]="severity()"
                (change)="severity.set($any($event.target).value)"
              >
                <option value="">—</option>
                @for (option of options; track option) {
                  <option [value]="option">{{ option }}</option>
                }
              </select>
            </label>
          }

          @if (entry.requiresFreeText) {
            <label>
              Opis
              <textarea
                data-testid="free-text-input"
                [value]="freeTextDescription()"
                (input)="freeTextDescription.set($any($event.target).value)"
              ></textarea>
            </label>
          }

          <label>
            Notatka
            <textarea data-testid="note-input" [value]="note()" (input)="note.set($any($event.target).value)"></textarea>
          </label>

          <label>
            Data
            <input
              type="date"
              data-testid="diagnosis-date-input"
              [value]="diagnosisDate()"
              (input)="diagnosisDate.set($any($event.target).value)"
            />
          </label>

          <button type="submit" data-testid="save-finding" [disabled]="!canSave()">
            {{ correctingFindingId() ? 'Zapisz korektę' : 'Zapisz' }}
          </button>
          <button type="button" data-testid="discard-finding" (click)="discard()">Anuluj</button>
        }
      </form>

      @if (saveState() === 'success') {
        <p data-testid="save-success" role="status">Zapisano.</p>
      }
      @if (saveState() === 'error') {
        <p data-testid="save-error" role="alert">Nie udało się zapisać wpisu. Spróbuj ponownie.</p>
      }
    </section>
  `,
  styles: `
    .detail-panel {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px;
      background: var(--pu-surface, #fff);
      border: 1px solid var(--pu-border, #e6dfd5);
      border-radius: 10px;
    }

    .presence-options,
    .canal-state-toggle {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .presence-options button[aria-pressed='true'],
    .canal-state-toggle button[aria-pressed='true'] {
      border-color: var(--pu-accent, #cbad89);
      background: var(--pu-accent, #cbad89);
      font-weight: 600;
    }
    .canal-state-toggle button {
      font-size: 12px;
      padding: 3px 8px;
      border: 1px solid var(--pu-border, #e6dfd5);
      background: var(--pu-surface, #fff);
      border-radius: 999px;
      cursor: pointer;
    }
    .canals ul {
      list-style: none;
      padding: 0;
      margin: 0 0 8px;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .canals li {
      display: flex;
      gap: 6px;
      align-items: center;
    }
    .add-canal-form {
      display: flex;
      gap: 6px;
    }
    .search-results {
      list-style: none;
      padding: 0;
      margin: 4px 0 0;
      max-height: 320px;
      overflow-y: auto;
      border: 1px solid var(--pu-border, #e6dfd5);
      border-radius: 8px;
    }
    .catalog-group-label {
      padding: 6px 10px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.02em;
      color: var(--pu-text-muted, #5c5654);
      background: var(--pu-surface-raised, #f2ede6);
    }
    .catalog-entry {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      text-align: left;
      border: 0;
      border-bottom: 1px solid var(--pu-border, #e6dfd5);
      background: none;
      padding: 7px 10px;
      cursor: pointer;
    }
    .catalog-entry:hover {
      background: var(--pu-surface-raised, #f2ede6);
    }
    .layer-dot {
      flex: none;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--pu-tooth-diseased-stroke, #b33);
    }
    .layer-dot-existing {
      background: var(--pu-tooth-restored-stroke, #3a6ea5);
    }
    .catalog-entry-name {
      flex: 1;
    }
    .catalog-entry-scope {
      flex: none;
      font-size: 11px;
      padding: 2px 8px;
      border-radius: 999px;
      border: 1px solid var(--pu-border, #e6dfd5);
      color: var(--pu-text-muted, #5c5654);
    }
    .catalog-entry-icd {
      flex: none;
      font-size: 11px;
      color: var(--pu-text-muted, #5c5654);
      min-width: 3.5em;
      text-align: right;
    }
    .close-form {
      display: flex;
      align-items: flex-end;
      gap: 8px;
      padding: 8px;
      border: 1px solid var(--pu-border, #e6dfd5);
      border-radius: 8px;
    }

    /* FR-006 — side-by-side on wide viewports, slide-over drawer on narrow ones; the content
       itself is identical, so the selected tooth is never hidden by either layout. */
    @media (max-width: 599px) {
      .detail-panel {
        position: fixed;
        inset: 0 0 0 auto;
        width: min(100%, 420px);
        z-index: 10;
        overflow-y: auto;
      }
    }
  `,
})
export class ToothDetailPanelComponent {
  readonly patientId = input.required<string>();
  readonly fdiNumber = input.required<number>();
  readonly position = input.required<ToothPosition>();
  /** FR-029a — set by a direct surface-zone click on the main diagram's middle strip, so the
   * surface is already marked here without the user having to open this panel first. */
  readonly presetSurface = input<ToothSurface | null>(null);

  readonly saved = output<ToothFinding>();
  readonly closeRequested = output<void>();
  /** FR-038/FR-065/FR-066/FR-068 — emitted after a presence/canal write succeeds, so
   * tooth-chart.component.ts re-fetches the chart the same way it does for `saved` (server is the
   * sole source of truth for the resulting state). */
  readonly positionChanged = output<void>();

  readonly presenceOptions: Array<{ value: ToothPresence; labelPl: string }> = [
    { value: 'PRESENT', labelPl: 'Obecny' },
    { value: 'EXTRACTED', labelPl: 'Usunięty' },
    { value: 'CONGENITALLY_MISSING', labelPl: 'Wrodzony brak' },
    { value: 'UNERUPTED', labelPl: 'Niewyrznięty' },
  ];

  /** Ported from the mockup's CANAL_STATES — three treatment states as toggle buttons (FR-066a),
   * matching the diagram's own red/green/green-with-red-apex non-color cue. */
  readonly canalStateOptions: Array<{ value: RootCanalState; labelPl: string }> = [
    { value: 'NEEDS_TREATMENT', labelPl: 'Do leczenia' },
    { value: 'TREATED', labelPl: 'Wyleczony' },
    { value: 'UNDERTREATED', labelPl: 'Niedoleczony' },
  ];

  readonly newCanalName = signal('');

  private readonly toothChartService = inject(ToothChartService);
  private readonly diagnosisCatalogService = inject(DiagnosisCatalogService);

  readonly query = signal('');
  readonly searchResults = signal<DiagnosisCatalogEntry[]>([]);
  readonly selectedEntry = signal<DiagnosisCatalogEntry | null>(null);
  readonly selectedSurfaces = signal<ToothSurface[]>([]);
  readonly severity = signal('');
  readonly freeTextDescription = signal('');
  readonly note = signal('');
  readonly diagnosisDate = signal(new Date().toISOString().slice(0, 10));
  readonly saveState = signal<SaveState>('idle');

  /** FR-034 — "historia zęba": collapsed by default, fetched lazily on first expansion. */
  readonly historyOpen = signal(false);
  readonly history = signal<ToothFinding[] | null>(null);

  /** FR-033 — set while the main form is pre-filled to correct this finding rather than create a
   * new one; save() routes to correctFinding instead of addFinding while this is set. */
  readonly correctingFindingId = signal<string | null>(null);

  /** FR-032 — "Zamknij rozpoznanie": a separate, minimal form (just the resolution date) shown
   * alongside the main one, keyed to the finding being closed. */
  readonly closingFindingId = signal<string | null>(null);
  readonly closeResolvedDate = signal('');

  readonly anatomicalName = computed(() => toothAnatomy(this.fdiNumber()).labelPl);

  readonly existingSurfaces = computed<ToothSurface[]>(() =>
    this.position()
      .currentFindings.flatMap((f) => f.surfaces ?? [])
      .filter((s, i, arr) => arr.indexOf(s) === i),
  );

  readonly canSelectSurfaces = computed(() => this.selectedEntry()?.anatomicalScope === 'SURFACE');

  readonly nonRemovedCanals = computed<RootCanal[]>(() =>
    this.position().canals.filter((c) => !c.removed),
  );

  /** Ported from the mockup's `dxPicker()` — the catalog search results grouped by category, in
   * the approved display order, category headers hidden when empty. */
  readonly groupedSearchResults = computed<Array<{ category: DiagnosisCategory; label: string; entries: DiagnosisCatalogEntry[] }>>(() => {
    const results = this.searchResults();
    return CATEGORY_ORDER.map((category) => ({
      category,
      label: CATEGORY_LABELS[category],
      entries: results.filter((e) => e.category === category),
    })).filter((group) => group.entries.length > 0);
  });

  scopeLabel(scope: AnatomicalScope): string {
    return SCOPE_LABELS[scope];
  }

  readonly canSave = computed(() => {
    const entry = this.selectedEntry();
    if (!entry) {
      return false;
    }
    if (entry.anatomicalScope === 'SURFACE' && this.selectedSurfaces().length === 0) {
      return false;
    }
    if (entry.requiresFreeText && !this.freeTextDescription().trim()) {
      return false;
    }
    return true;
  });

  constructor() {
    effect((onCleanup) => {
      const q = this.query();
      let cancelled = false;
      this.diagnosisCatalogService.search(q).subscribe((results) => {
        if (!cancelled) {
          this.searchResults.set(results);
        }
      });
      onCleanup(() => (cancelled = true));
    });

    effect(() => {
      const surface = this.presetSurface();
      if (surface) {
        this.selectedSurfaces.update((current) =>
          current.includes(surface) ? current : [...current, surface],
        );
      }
    });
  }

  onQueryChange(value: string): void {
    this.query.set(value);
  }

  selectEntry(entry: DiagnosisCatalogEntry): void {
    this.selectedEntry.set(entry);
    this.selectedSurfaces.set([]);
    this.searchResults.set([]);
    this.query.set(entry.namePl);
  }

  toggleHistory(): void {
    const opening = !this.historyOpen();
    this.historyOpen.set(opening);
    if (opening && this.history() === null) {
      this.toothChartService
        .getPositionHistory(this.patientId(), this.fdiNumber())
        .subscribe((history) => this.history.set(history));
    }
  }

  toggleSurface(surface: ToothSurface): void {
    this.selectedSurfaces.update((current) =>
      current.includes(surface) ? current.filter((s) => s !== surface) : [...current, surface],
    );
  }

  save(event: Event): void {
    event.preventDefault();
    const entry = this.selectedEntry();
    if (!entry || !this.canSave()) {
      return;
    }
    this.saveState.set('saving');
    const request = {
      fdiNumber: this.fdiNumber(),
      diagnosisCatalogEntryId: entry.id,
      surfaces: entry.anatomicalScope === 'SURFACE' ? this.selectedSurfaces() : null,
      severity: this.severity() || null,
      freeTextDescription: this.freeTextDescription() || null,
      note: this.note() || null,
      diagnosisDate: this.diagnosisDate(),
    };
    const correctingId = this.correctingFindingId();
    const request$ = correctingId
      ? this.toothChartService.correctFinding(this.patientId(), correctingId, request)
      : this.toothChartService.addFinding(this.patientId(), request);
    this.diagnosisCatalogService.withRecencyTracking(entry.code, request$).subscribe({
      next: (finding) => {
        this.saveState.set('success');
        this.resetForm();
        this.saved.emit(finding);
      },
      error: () => {
        // FR-071 — field values are deliberately NOT cleared here so the user never re-enters them.
        this.saveState.set('error');
      },
    });
  }

  /** FR-033 — "Koryguj": pre-fills the main form with this finding's current values; save()
   * then submits through correctFinding (supersede) instead of addFinding. */
  startCorrect(finding: ToothFinding): void {
    this.closingFindingId.set(null);
    this.correctingFindingId.set(finding.id);
    this.selectedEntry.set(finding.diagnosisCatalogEntry);
    this.query.set(finding.diagnosisCatalogEntry.namePl);
    this.searchResults.set([]);
    this.selectedSurfaces.set(finding.surfaces ?? []);
    this.severity.set(finding.severity ?? '');
    this.freeTextDescription.set(finding.freeTextDescription ?? '');
    this.note.set(finding.note ?? '');
    this.diagnosisDate.set(finding.diagnosisDate);
  }

  cancelCorrect(): void {
    this.correctingFindingId.set(null);
    this.resetForm();
  }

  /** FR-032 — "Zamknij rozpoznanie": opens the minimal close form for this finding. */
  startClose(findingId: string): void {
    this.correctingFindingId.set(null);
    this.closingFindingId.set(findingId);
    this.closeResolvedDate.set('');
  }

  cancelClose(): void {
    this.closingFindingId.set(null);
    this.closeResolvedDate.set('');
  }

  submitClose(event: Event, findingId: string): void {
    event.preventDefault();
    if (!this.closeResolvedDate()) {
      return;
    }
    this.toothChartService
      .closeFinding(this.patientId(), findingId, { resolvedDate: this.closeResolvedDate() })
      .subscribe({
        next: (finding) => {
          this.cancelClose();
          this.saveState.set('success');
          this.saved.emit(finding);
        },
        error: () => this.saveState.set('error'),
      });
  }

  /** FR-038/FR-070 — sets a position's presence, echoing back its version as expectedVersion. */
  setPresence(presence: ToothPresence): void {
    this.toothChartService
      .changePresence(this.patientId(), this.fdiNumber(), {
        presence,
        presenceDate: new Date().toISOString().slice(0, 10),
        expectedVersion: this.position().version,
      })
      .subscribe(() => this.positionChanged.emit());
  }

  /** FR-065 — adds a root canal (server-side enforces the max-6 and PRESENT-only rules). */
  submitAddCanal(): void {
    const name = this.newCanalName().trim();
    if (!name) {
      return;
    }
    this.toothChartService
      .addCanal(this.patientId(), this.fdiNumber(), { name })
      .subscribe(() => {
        this.newCanalName.set('');
        this.positionChanged.emit();
      });
  }

  /** FR-065/FR-070 — rename, echoing back the canal's version as expectedVersion. */
  renameCanal(canal: RootCanal, name: string): void {
    if (!name.trim() || name === canal.name) {
      return;
    }
    this.toothChartService
      .updateCanal(this.patientId(), this.fdiNumber(), canal.id, {
        name,
        expectedVersion: canal.version,
      })
      .subscribe(() => this.positionChanged.emit());
  }

  /** FR-066/FR-070 — change treatment state, echoing back the canal's version as expectedVersion. */
  changeCanalState(canal: RootCanal, state: RootCanalState): void {
    this.toothChartService
      .updateCanal(this.patientId(), this.fdiNumber(), canal.id, {
        state,
        expectedVersion: canal.version,
      })
      .subscribe(() => this.positionChanged.emit());
  }

  /** FR-068 — soft delete only; findings that reference this canal are never removed or hidden. */
  removeCanal(canal: RootCanal): void {
    this.toothChartService
      .removeCanal(this.patientId(), this.fdiNumber(), canal.id)
      .subscribe(() => this.positionChanged.emit());
  }

  discard(): void {
    if (this.isDirty() && !window.confirm('Odrzucić niezapisany wpis?')) {
      return;
    }
    this.resetForm();
    this.closeRequested.emit();
  }

  private isDirty(): boolean {
    return (
      this.selectedEntry() !== null ||
      this.note().trim().length > 0 ||
      this.freeTextDescription().trim().length > 0
    );
  }

  private resetForm(): void {
    this.query.set('');
    this.searchResults.set([]);
    this.selectedEntry.set(null);
    this.selectedSurfaces.set([]);
    this.severity.set('');
    this.freeTextDescription.set('');
    this.note.set('');
    this.diagnosisDate.set(new Date().toISOString().slice(0, 10));
    this.correctingFindingId.set(null);
  }
}
