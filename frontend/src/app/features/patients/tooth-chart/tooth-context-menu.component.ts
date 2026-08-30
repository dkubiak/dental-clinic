import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DiagnosisCatalogEntry, ToothFinding, ToothSurface } from '../patients.models';
import { DiagnosisCatalogService } from './diagnosis-catalog.service';
import { ToothChartService } from './tooth-chart.service';

/**
 * FR-020a/FR-020b (US1 Acceptance Scenario 8; US6 extension) — right-click/long-press quick-add:
 * lists recently-used and quick-access catalog entries, saves the chosen entry immediately (no
 * full form), and offers an instant undo implemented as a correct-supersede call (closeFinding —
 * the closest supersede-based primitive, research.md D3). Built once here in US1 for the
 * single-tooth path; US6 (T121/T122) extends it to apply to an active multi-selection instead —
 * this file is not duplicated or rebuilt there.
 */
@Component({
  selector: 'app-tooth-context-menu',
  standalone: true,
  template: `
    @if (open()) {
      <div
        class="context-menu"
        role="menu"
        data-testid="tooth-context-menu"
        [style.left.px]="x()"
        [style.top.px]="y()"
      >
        @if (undoTarget(); as target) {
          <button type="button" data-testid="context-menu-undo" (click)="undo(target)">
            Cofnij: {{ target.diagnosisCatalogEntry.namePl }}
          </button>
        }

        @if (recentEntries().length > 0) {
          <div class="section">
            <p class="section-label">Ostatnio używane</p>
            @for (entry of recentEntries(); track entry.id) {
              <button
                type="button"
                [attr.data-testid]="'context-menu-recent-' + entry.code"
                (click)="pick(entry)"
              >
                {{ entry.namePl }}
              </button>
            }
          </div>
        }

        <div class="section">
          <p class="section-label">Najczęstsze</p>
          @for (entry of quickAccessEntries(); track entry.id) {
            <button
              type="button"
              [attr.data-testid]="'context-menu-quick-' + entry.code"
              (click)="pick(entry)"
            >
              {{ entry.namePl }}
            </button>
          }
        </div>
      </div>
    }
  `,
  styles: `
    .context-menu {
      position: fixed;
      z-index: 20;
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 8px;
      background: var(--pu-surface, #fff);
      border: 1px solid var(--pu-border, #e6dfd5);
      border-radius: 8px;
      box-shadow: 0 6px 22px rgb(0 0 0 / 0.12);
      min-width: 200px;
    }
    .section-label {
      margin: 4px 0 0;
      font-size: 11px;
      color: var(--pu-text-muted, #5c5654);
    }
    button {
      text-align: left;
      background: none;
      border: none;
      padding: 6px 8px;
      cursor: pointer;
      border-radius: 4px;
    }
    button:hover {
      background: var(--pu-surface-raised, #f2ede6);
    }
  `,
})
export class ToothContextMenuComponent {
  readonly open = input.required<boolean>();
  readonly x = input<number>(0);
  readonly y = input<number>(0);
  readonly patientId = input.required<string>();
  readonly fdiNumber = input.required<number>();
  /** When set (invoked on a surface zone), a SURFACE-scope entry applies to this surface. */
  readonly targetSurface = input<ToothSurface | null>(null);
  /** The most recent CURRENT finding on this tooth, offered as the quick "Cofnij" target. */
  readonly undoTarget = input<ToothFinding | null>(null);

  readonly saved = output<ToothFinding>();
  readonly closed = output<void>();

  private readonly toothChartService = inject(ToothChartService);
  private readonly diagnosisCatalogService = inject(DiagnosisCatalogService);

  private readonly allEntries = signal<DiagnosisCatalogEntry[]>([]);

  constructor() {
    this.diagnosisCatalogService.search().subscribe((entries) => this.allEntries.set(entries));
  }

  private readonly applicableEntries = computed(() =>
    this.targetSurface()
      ? this.allEntries()
      : this.allEntries().filter((e) => e.anatomicalScope !== 'SURFACE'),
  );

  readonly recentEntries = computed(() =>
    this.diagnosisCatalogService.recentEntries(this.applicableEntries()),
  );

  readonly quickAccessEntries = computed(() => {
    const recentCodes = new Set(this.recentEntries().map((e) => e.code));
    return this.applicableEntries().filter((e) => e.quickAccess && !recentCodes.has(e.code));
  });

  pick(entry: DiagnosisCatalogEntry): void {
    const surfaces = entry.anatomicalScope === 'SURFACE' ? this.surfacesFor() : null;
    const request$ = this.toothChartService.addFinding(this.patientId(), {
      fdiNumber: this.fdiNumber(),
      diagnosisCatalogEntryId: entry.id,
      surfaces,
      diagnosisDate: new Date().toISOString().slice(0, 10),
    });
    this.diagnosisCatalogService.withRecencyTracking(entry.code, request$).subscribe((finding) => {
      this.saved.emit(finding);
      this.close();
    });
  }

  undo(finding: ToothFinding): void {
    this.toothChartService
      .closeFinding(this.patientId(), finding.id, {
        resolvedDate: new Date().toISOString().slice(0, 10),
        note: 'Cofnięte przez operatora (szybkie menu).',
      })
      .subscribe((closed) => {
        this.saved.emit(closed);
        this.close();
      });
  }

  close(): void {
    this.closed.emit();
  }

  private surfacesFor(): ToothSurface[] {
    const surface = this.targetSurface();
    return surface ? [surface] : [];
  }
}
