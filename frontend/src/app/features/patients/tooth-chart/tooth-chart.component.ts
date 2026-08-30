import { Component, computed, input, OnInit, inject, signal } from '@angular/core';
import { FindingLayer, ToothChart, ToothFinding, ToothPosition, ToothSurface } from '../patients.models';
import { ToothChartService } from './tooth-chart.service';
import { ToothArchComponent } from './tooth-arch.component';
import { ToothContextMenuComponent } from './tooth-context-menu.component';
import { ToothDetailPanelComponent } from './tooth-detail-panel.component';
import { SurfaceMapComponent } from './surface-map.component';
import { toothAnatomy } from './tooth-geometry';

type LoadState = 'loading' | 'loaded' | 'error';
type LayerFilter = 'ALL' | FindingLayer;
/** FR-029b — surface-map `size` per zoom level, chosen against the real zoneDefs() geometry
 * (zoneDefs' narrowest zone bounding dimension is 0.3 * size): 90 clears the >=24px target,
 * 160 clears the >=44px target (and matches the size already used for the detail-panel's own
 * enlarged map, tooth-detail-panel.component.ts). Level 1 is the compact default view and makes
 * no size guarantee. */
const ZOOM_SIZES: Record<1 | 2 | 3, number> = { 1: 40, 2: 90, 3: 160 };

/**
 * US1-US6 container: fetches the chart, splits positions into the two arches, owns the
 * selection-state signal, and presents the FR-054 loading/empty/error states (research.md D11).
 * Reachable from patient-detail as the odontogram tab.
 */
@Component({
  selector: 'app-tooth-chart',
  standalone: true,
  imports: [
    ToothArchComponent,
    ToothDetailPanelComponent,
    ToothContextMenuComponent,
    SurfaceMapComponent,
  ],
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
        <!-- FR-070/SC-010 — a stale write is never lost silently: whichever child component
             triggered it, one reload prompt appears here. -->
        @if (conflictMessage(); as message) {
          <div class="conflict-banner" data-testid="conflict-banner" role="alert">
            <p>{{ message }}</p>
            <button type="button" data-testid="conflict-reload" (click)="reloadAfterConflict()">
              Przeładuj
            </button>
          </div>
        }

        @if (isEmpty()) {
          <p data-testid="tooth-chart-empty">Brak odnotowanych zmian — wszystkie zęby zdrowe.</p>
        }

        <!-- FR-008 — always reachable, explains every state/layer/surface symbol in Polish. -->
        <details class="legend" data-testid="tooth-chart-legend">
          <summary>Legenda oznaczeń</summary>
          <section aria-label="Stan pozycji">
            <h4>Stan pozycji</h4>
            <ul>
              <li><span class="swatch swatch-healthy"></span>Ząb zdrowy</li>
              <li><span class="swatch swatch-diseased"></span>Ząb z rozpoznaniem</li>
              <li><span class="swatch swatch-restored"></span>Ząb ze stanem istniejącym (np. wypełnienie)</li>
              <li><span class="swatch swatch-absent"></span>Brak zęba / ząb usunięty</li>
              <li><span class="swatch swatch-unerupted"></span>Niewyrznięty / ząb zatrzymany</li>
            </ul>
          </section>
          <section aria-label="Warstwa wpisu">
            <h4>Warstwa wpisu</h4>
            <ul>
              <li><span class="swatch swatch-diseased"></span>Rozpoznanie</li>
              <li><span class="swatch swatch-restored"></span>Stan istniejący</li>
            </ul>
          </section>
          <section aria-label="Symbolika powierzchni">
            <h4>Powierzchnie zęba</h4>
            <ul>
              <li>Góra schematu — powierzchnia przedsionkowa (wargowa / policzkowa)</li>
              <li>Dół schematu — powierzchnia językowa / podniebienna</li>
              <li>Boki schematu — powierzchnia mezjalna i dystalna</li>
              <li>Środek schematu — powierzchnia sieczna (siekacze, kły) lub żująca (przedtrzonowce, trzonowce)</li>
            </ul>
          </section>
        </details>

        <div class="controls">
          <!-- FR-009 — client-side only, never touches the fetched chart data. -->
          <div class="layer-filter" role="group" aria-label="Filtr warstwy wpisu">
            <button
              type="button"
              data-testid="layer-filter-all"
              [attr.aria-pressed]="layerFilter() === 'ALL'"
              (click)="layerFilter.set('ALL')"
            >
              Wszystkie
            </button>
            <button
              type="button"
              data-testid="layer-filter-diagnosis"
              [attr.aria-pressed]="layerFilter() === 'DIAGNOSIS'"
              (click)="layerFilter.set('DIAGNOSIS')"
            >
              Rozpoznanie
            </button>
            <button
              type="button"
              data-testid="layer-filter-existing"
              [attr.aria-pressed]="layerFilter() === 'EXISTING_STATE'"
              (click)="layerFilter.set('EXISTING_STATE')"
            >
              Stan istniejący
            </button>
          </div>

          <!-- FR-029b/FR-049 — at least two enlargement steps above the default 1x view. -->
          <div class="zoom-control" role="group" aria-label="Powiększenie diagramu">
            <button type="button" data-testid="zoom-1" [attr.aria-pressed]="zoomLevel() === 1" (click)="setZoom(1)">1×</button>
            <button type="button" data-testid="zoom-2" [attr.aria-pressed]="zoomLevel() === 2" (click)="setZoom(2)">2×</button>
            <button type="button" data-testid="zoom-3" [attr.aria-pressed]="zoomLevel() === 3" (click)="setZoom(3)">3×</button>
          </div>
        </div>

        <div class="odontogram-layout">
          <!-- FR-049 — any horizontal overflow from zooming scrolls inside this container only,
               never the whole page. -->
          <div
            class="diagram-scroll-container"
            data-testid="diagram-scroll-container"
            [style.overflow-x]="'auto'"
          >
            <div class="odontogram">
              <app-tooth-arch
                [positions]="upperPositions()"
                [dir]="1"
                [layerFilter]="layerFilter()"
                (toothSelected)="select($event)"
                (toothContextMenu)="openContextMenu($event)"
              />

              <!-- FR-029 — the middle-strip surface-map row: one instance per visible tooth
                   column, aligned to its own arch's row, sitting between the two arches. -->
              <div class="surface-strip" data-testid="surface-strip">
                <div class="surface-row" data-testid="surface-row-upper">
                  @for (position of upperPositions(); track position.fdiNumber) {
                    <div
                      class="surface-cell"
                      [attr.data-testid]="'surface-cell-' + position.fdiNumber"
                      [style.width.px]="zoomSize()"
                    >
                      <app-surface-map
                        [fdiNumber]="position.fdiNumber"
                        [size]="zoomSize()"
                        [existingSurfaces]="existingSurfacesFor(position)"
                        (surfaceToggled)="onSurfaceZoneClicked(position.fdiNumber, $event)"
                      />
                    </div>
                  }
                </div>
                <div class="surface-row" data-testid="surface-row-lower">
                  @for (position of lowerPositions(); track position.fdiNumber) {
                    <div
                      class="surface-cell"
                      [attr.data-testid]="'surface-cell-' + position.fdiNumber"
                      [style.width.px]="zoomSize()"
                    >
                      <app-surface-map
                        [fdiNumber]="position.fdiNumber"
                        [size]="zoomSize()"
                        [existingSurfaces]="existingSurfacesFor(position)"
                        (surfaceToggled)="onSurfaceZoneClicked(position.fdiNumber, $event)"
                      />
                    </div>
                  }
                </div>
              </div>

              <app-tooth-arch
                [positions]="lowerPositions()"
                [dir]="-1"
                [layerFilter]="layerFilter()"
                (toothSelected)="select($event)"
                (toothContextMenu)="openContextMenu($event)"
              />
            </div>
          </div>

          @if (selectedPosition(); as position) {
            <app-tooth-detail-panel
              [patientId]="patientId()"
              [fdiNumber]="position.fdiNumber"
              [position]="position"
              [presetSurface]="presetSurfaceFor(position.fdiNumber)"
              (saved)="onSaved()"
              (closeRequested)="onPanelClosed()"
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
    .diagram-scroll-container {
      max-width: 100%;
      overflow-x: auto;
    }
    .odontogram {
      display: flex;
      flex-direction: column;
      gap: 12px;
      align-items: center;
      width: max-content;
      margin: 0 auto;
    }
    .surface-strip {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .surface-row {
      display: flex;
      gap: 4px;
      justify-content: center;
    }
    .surface-cell {
      flex: none;
    }
    .controls {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin: 12px 0;
    }
    .layer-filter,
    .zoom-control {
      display: inline-flex;
      gap: 4px;
    }
    .layer-filter button,
    .zoom-control button {
      border: 1px solid var(--pu-border, #e6dfd5);
      background: var(--pu-surface, #fff);
      border-radius: 6px;
      padding: 4px 10px;
      cursor: pointer;
    }
    .layer-filter button[aria-pressed='true'],
    .zoom-control button[aria-pressed='true'] {
      border-color: var(--pu-accent, #cbad89);
      background: var(--pu-accent, #cbad89);
      font-weight: 600;
    }
    .conflict-banner {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 16px;
      margin: 12px 0;
      background: var(--pu-tooth-diseased-fill, #f6cccc);
      border: 1px solid var(--pu-tooth-diseased-stroke, #b33);
      border-radius: 8px;
    }
    .conflict-banner button {
      border: 1px solid var(--pu-tooth-diseased-stroke, #b33);
      background: var(--pu-surface, #fff);
      border-radius: 6px;
      padding: 4px 12px;
      cursor: pointer;
    }
    .legend {
      margin: 12px 0;
    }
    .legend ul {
      list-style: none;
      padding: 0;
      margin: 4px 0 12px;
    }
    .legend li {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 2px 0;
    }
    .swatch {
      display: inline-block;
      width: 12px;
      height: 12px;
      border-radius: 3px;
      border: 1px solid var(--pu-tooth-healthy-stroke, #888);
      background: var(--pu-tooth-healthy-fill, #fff);
    }
    .swatch-diseased {
      background: var(--pu-tooth-diseased-fill, #f6cccc);
      border-color: var(--pu-tooth-diseased-stroke, #b33);
    }
    .swatch-restored {
      background: var(--pu-tooth-restored-fill, #cfe3f7);
      border-color: var(--pu-tooth-restored-stroke, #3a6ea5);
    }
    .swatch-absent {
      background: none;
      border-color: var(--pu-tooth-absent, #999);
      border-style: dashed;
    }
    .swatch-unerupted {
      opacity: 0.45;
      border-style: dashed;
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

  /** FR-009 — purely a view filter, never mutates chart(). */
  readonly layerFilter = signal<LayerFilter>('ALL');
  /** FR-029b — 1x is the compact default; 2x/3x are the FR-029b enlargement steps. */
  readonly zoomLevel = signal<1 | 2 | 3>(1);
  readonly zoomSize = computed(() => ZOOM_SIZES[this.zoomLevel()]);
  /** FR-029a — set by a direct surface-zone click on the middle strip so the detail panel opens
   * with that tooth+surface already marked, instead of requiring the panel to be opened first. */
  readonly presetSurface = signal<{ fdiNumber: number; surface: ToothSurface } | null>(null);

  /** FR-070/SC-010 — set from ToothChartService.conflict$ whenever any write (from this
   * component or a child) hits a 409; cleared once the user reloads. */
  readonly conflictMessage = signal<string | null>(null);

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

  constructor() {
    this.toothChartService.conflict$.subscribe((message) => this.conflictMessage.set(message));
  }

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
    this.presetSurface.set(null);
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

  onPanelClosed(): void {
    this.selectedFdiNumber.set(null);
    this.presetSurface.set(null);
  }

  /** FR-070/SC-010 — the reload prompt's action: dismiss the banner and re-fetch the chart so the
   * user sees the current, authoritative state instead of retrying blind against stale data. */
  reloadAfterConflict(): void {
    this.conflictMessage.set(null);
    this.refreshChart();
  }

  existingSurfacesFor(position: ToothPosition): ToothSurface[] {
    return position.currentFindings
      .flatMap((f) => f.surfaces ?? [])
      .filter((s, i, arr) => arr.indexOf(s) === i);
  }

  presetSurfaceFor(fdiNumber: number): ToothSurface | null {
    const preset = this.presetSurface();
    return preset?.fdiNumber === fdiNumber ? preset.surface : null;
  }

  /** FR-029a — a direct surface-zone click on the middle strip selects the matching tooth+surface
   * immediately; opening/updating the detail panel is the RESULT, not a precondition. */
  onSurfaceZoneClicked(fdiNumber: number, surface: ToothSurface): void {
    this.selectedFdiNumber.set(fdiNumber);
    this.contextMenu.set(null);
    this.presetSurface.set({ fdiNumber, surface });
  }

  /** FR-029b — keeps the selected tooth's surface-map cell in view across zoom-level changes; the
   * cell itself (data-testid) is stable across zoom levels (only its size changes), so this can
   * run synchronously without waiting for a render pass. */
  setZoom(level: 1 | 2 | 3): void {
    this.zoomLevel.set(level);
    const fdiNumber = this.selectedFdiNumber();
    if (fdiNumber !== null) {
      document
        .querySelector(`[data-testid="surface-cell-${fdiNumber}"]`)
        ?.scrollIntoView({ inline: 'center', block: 'nearest' });
    }
  }
}
