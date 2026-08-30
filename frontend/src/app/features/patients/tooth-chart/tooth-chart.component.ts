import { Component, computed, input, OnInit, inject, signal } from '@angular/core';
import { ToothChart, ToothFinding, ToothPosition } from '../patients.models';
import { ToothChartService } from './tooth-chart.service';
import { ToothArchComponent } from './tooth-arch.component';
import { ToothContextMenuComponent } from './tooth-context-menu.component';
import { ToothDetailPanelComponent } from './tooth-detail-panel.component';
import { toothAnatomy } from './tooth-geometry';

type LoadState = 'loading' | 'loaded' | 'error';

/**
 * US1-US6 container: fetches the chart, splits positions into the two arches, owns the
 * selection-state signal, and presents the FR-054 loading/empty/error states (research.md D11).
 * Reachable from patient-detail as the odontogram tab.
 */
@Component({
  selector: 'app-tooth-chart',
  standalone: true,
  imports: [ToothArchComponent, ToothDetailPanelComponent, ToothContextMenuComponent],
  template: `
    @switch (loadState()) {
      @case ('loading') {
        <p data-testid="tooth-chart-loading">Wczytywanie odontogramu…</p>
      }
      @case ('error') {
        <p data-testid="tooth-chart-error" role="alert">
          Nie udało się wczytać odontogramu. Spróbuj ponownie.
        </p>
      }
      @case ('loaded') {
        @if (isEmpty()) {
          <p data-testid="tooth-chart-empty">Brak odnotowanych zmian — wszystkie zęby zdrowe.</p>
        }
        <div class="odontogram-layout">
          <div class="odontogram">
            <app-tooth-arch
              [positions]="upperPositions()"
              [dir]="1"
              (toothSelected)="select($event)"
              (toothContextMenu)="openContextMenu($event)"
            />
            <app-tooth-arch
              [positions]="lowerPositions()"
              [dir]="-1"
              (toothSelected)="select($event)"
              (toothContextMenu)="openContextMenu($event)"
            />
          </div>

          @if (selectedPosition(); as position) {
            <app-tooth-detail-panel
              [patientId]="patientId()"
              [fdiNumber]="position.fdiNumber"
              [position]="position"
              (saved)="onSaved()"
              (closeRequested)="selectedFdiNumber.set(null)"
            />
          }
        </div>

        <app-tooth-context-menu
          [open]="contextMenu() !== null"
          [x]="contextMenu()?.x ?? 0"
          [y]="contextMenu()?.y ?? 0"
          [patientId]="patientId()"
          [fdiNumber]="contextMenu()?.fdiNumber ?? 0"
          [undoTarget]="contextMenuUndoTarget()"
          (saved)="onSaved()"
          (closed)="contextMenu.set(null)"
        />
      }
    }
  `,
  styles: `
    .odontogram-layout {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }
    .odontogram {
      display: flex;
      flex-direction: column;
      gap: 24px;
      align-items: center;
    }
  `,
})
export class ToothChartComponent implements OnInit {
  readonly patientId = input.required<string>();

  private readonly toothChartService = inject(ToothChartService);

  readonly loadState = signal<LoadState>('loading');
  readonly chart = signal<ToothChart | null>(null);
  readonly selectedFdiNumber = signal<number | null>(null);
  readonly contextMenu = signal<{ fdiNumber: number; x: number; y: number } | null>(null);

  /** FR-043/FR-046 — dentitionMode is a pure view filter over the 52 positions that always exist
   * (research.md D2); MIXED shows both dentition types, PERMANENT/DECIDUOUS show only their own. */
  readonly visiblePositions = computed<ToothPosition[]>(() => {
    const chart = this.chart();
    if (!chart) {
      return [];
    }
    return chart.dentitionMode === 'MIXED'
      ? chart.positions
      : chart.positions.filter((p) => p.dentitionType === chart.dentitionMode);
  });

  readonly upperPositions = computed<ToothPosition[]>(() =>
    this.visiblePositions().filter((p) => toothAnatomy(p.fdiNumber).upper),
  );

  readonly lowerPositions = computed<ToothPosition[]>(() =>
    this.visiblePositions().filter((p) => !toothAnatomy(p.fdiNumber).upper),
  );

  readonly isEmpty = computed(
    () => this.visiblePositions().every((p) => p.currentFindings.length === 0),
  );

  readonly selectedPosition = computed<ToothPosition | null>(
    () =>
      (this.chart()?.positions ?? []).find((p) => p.fdiNumber === this.selectedFdiNumber()) ?? null,
  );

  readonly contextMenuUndoTarget = computed<ToothFinding | null>(() => {
    const menu = this.contextMenu();
    if (!menu) {
      return null;
    }
    const position = (this.chart()?.positions ?? []).find((p) => p.fdiNumber === menu.fdiNumber);
    return position?.currentFindings.at(-1) ?? null;
  });

  ngOnInit(): void {
    this.loadState.set('loading');
    this.toothChartService.getChart(this.patientId()).subscribe({
      next: (chart) => {
        this.chart.set(chart);
        this.loadState.set('loaded');
      },
      error: () => this.loadState.set('error'),
    });
  }

  /** Swaps in fresh chart data in place, WITHOUT flipping loadState back to 'loading' — doing so
   * would tear down and remount the detail panel/context menu, wiping the FR-056 save-success
   * message before the user ever sees it. */
  private refreshChart(): void {
    this.toothChartService.getChart(this.patientId()).subscribe((chart) => this.chart.set(chart));
  }

  select(fdiNumber: number): void {
    this.selectedFdiNumber.set(fdiNumber);
    this.contextMenu.set(null);
  }

  openContextMenu(event: { fdiNumber: number; x: number; y: number }): void {
    this.contextMenu.set(event);
  }

  /** FR-004 acceptance scenario 4 — re-render the diagram to show "z aktywnym rozpoznaniem" after
   * a successful save (a fresh read, not a client-side patch, since the server is the sole source
   * of truth for the resulting state). */
  onSaved(): void {
    this.refreshChart();
  }
}
