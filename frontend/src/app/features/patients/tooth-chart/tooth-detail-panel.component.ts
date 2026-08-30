import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { DiagnosisCatalogEntry, ToothFinding, ToothPosition, ToothSurface } from '../patients.models';
import { DiagnosisCatalogService } from './diagnosis-catalog.service';
import { ToothChartService } from './tooth-chart.service';
import { SurfaceMapComponent } from './surface-map.component';
import { toothAnatomy } from './tooth-geometry';

type SaveState = 'idle' | 'saving' | 'success' | 'error';

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
            @for (entry of searchResults(); track entry.id) {
              <li>
                <button type="button" [attr.data-testid]="'catalog-entry-' + entry.code" (click)="selectEntry(entry)">
                  {{ entry.namePl }}
                </button>
              </li>
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
