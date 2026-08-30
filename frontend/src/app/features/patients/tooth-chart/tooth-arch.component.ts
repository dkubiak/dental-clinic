import { Component, computed, input, output } from '@angular/core';
import { FindingLayer, ToothPosition } from '../patients.models';
import {
  canalNodes,
  CROWN_ROOT_RATIO,
  crownPath,
  rootGeometry,
  toothAnatomy,
} from './tooth-geometry';

interface RenderedTooth {
  position: ToothPosition;
  fdiNumber: number;
  x: number;
  crownD: string;
  rootD: string;
  canalLines: Array<{ d: string; cls: string }>;
  canalDots: Array<{ x: number; y: number; treat: boolean }>;
  statusClass: 'healthy' | 'diseased' | 'restored' | 'absent' | 'unerupted';
  /** FR-046 — mixed-dentition mode renders deciduous and permanent positions simultaneously;
   * this must be visually distinguishable by more than the FDI numbering alone. */
  deciduous: boolean;
  /** FR-009 — true when this tooth's shown status is driven solely by a layer the current
   * layer-filter excludes; the status class itself is left untouched (FR-039/FR-050 non-color
   * cue), only an opacity-reducing class is layered on top. */
  dimmed: boolean;
  /** FR-010/edge case — the diagram can't render every finding distinctly, so a small badge
   * signals "wiele wpisów" and points the user to the full list in the detail panel. */
  hasMultipleFindings: boolean;
  labelPl: string;
  ariaLabel: string;
}

const COLUMN_WIDTH = 40;
const CROWN_H = 26;
/** More active findings than this can't be told apart on the compact tooth silhouette. */
const MULTI_FINDING_THRESHOLD = 3;

/**
 * FR-001/FR-002/FR-039/FR-052/FR-053 — renders one arch (upper or lower) procedurally from
 * tooth-geometry.ts, teeth oriented crowns-together/roots-outward, with quadrant labels and
 * keyboard/screen-reader support. Used twice by tooth-chart.component.ts (research.md D11).
 */
@Component({
  selector: 'app-tooth-arch',
  standalone: true,
  template: `
    <svg
      [attr.viewBox]="viewBox()"
      class="arch"
      [class.arch-upper]="dir() === 1"
      [class.arch-lower]="dir() === -1"
      role="img"
      [attr.aria-label]="dir() === 1 ? 'Łuk górny' : 'Łuk dolny'"
    >
      @for (label of quadrantLabels(); track label.quadrant) {
        <text [attr.x]="label.x" [attr.y]="label.y" class="quadrant-label">{{ label.quadrant }}</text>
      }
      @for (tooth of renderedTeeth(); track tooth.fdiNumber; let i = $index) {
        <g
          [attr.transform]="'translate(' + tooth.x + ' 0)'"
          class="tooth"
          [class]="'tooth status-' + tooth.statusClass"
          [class.layer-dimmed]="tooth.dimmed"
          [class.tooth-deciduous]="tooth.deciduous"
          [class.tooth-multi-selected]="isMultiSelected(tooth.fdiNumber)"
          [attr.data-testid]="'tooth-' + tooth.fdiNumber"
          [attr.tabindex]="0"
          [attr.role]="'button'"
          [attr.aria-label]="tooth.ariaLabel"
          [attr.aria-pressed]="selectedFdiNumbers().length > 0 ? isMultiSelected(tooth.fdiNumber) : null"
          (click)="select(tooth.fdiNumber)"
          (keydown.enter)="select(tooth.fdiNumber)"
          (keydown.arrowright)="onArrowKey($any($event), i)"
          (keydown.arrowleft)="onArrowKey($any($event), i)"
          (contextmenu)="onContextMenu($event, tooth.fdiNumber)"
          (pointerdown)="onPointerDown($event, tooth.fdiNumber)"
          (pointerenter)="onPointerEnter(tooth.fdiNumber)"
          (pointerup)="onPointerUp()"
          (pointerleave)="onPointerUp()"
        >
          <path [attr.d]="tooth.rootD" class="root" />
          <path [attr.d]="tooth.crownD" class="crown" />
          @for (line of tooth.canalLines; track line.d) {
            <path [attr.d]="line.d" [class]="line.cls" />
          }
          @for (dot of tooth.canalDots; track dot.x) {
            <circle [attr.cx]="dot.x" [attr.cy]="dot.y" r="2" [class.canal-dot-treat]="dot.treat" class="canal-dot" />
          }
          @if (tooth.hasMultipleFindings) {
            <text
              [attr.x]="0"
              [attr.y]="dir() === 1 ? -14 : 54"
              class="multi-indicator"
              [attr.data-testid]="'tooth-' + tooth.fdiNumber + '-multi-indicator'"
            >
              <title>wiele wpisów</title>
              ✳
            </text>
          }
        </g>
      }
    </svg>
  `,
  styles: `
    .arch {
      width: 100%;
      max-width: 640px;
      overflow: visible;
    }
    .tooth {
      cursor: pointer;
    }
    .crown {
      fill: var(--pu-tooth-healthy-fill, #fff);
      stroke: var(--pu-tooth-healthy-stroke, #888);
      stroke-width: 1.5;
    }
    .root {
      fill: var(--pu-tooth-root-fill, #eae0d2);
      stroke: var(--pu-tooth-healthy-stroke, #888);
      stroke-width: 1.5;
    }
    /* FR-039/FR-050 — diseased/restored/healthy must never rely on fill/stroke color alone: a
       thicker solid outline flags an active diagnosis, a fine dotted outline flags an existing
       (already-treated) state, so the three read apart in grayscale or with a color-vision
       deficiency. */
    .tooth.status-diseased .crown {
      fill: var(--pu-tooth-diseased-fill, #f6cccc);
      stroke: var(--pu-tooth-diseased-stroke, #b33);
      stroke-width: 2.5;
    }
    .tooth.status-restored .crown {
      fill: var(--pu-tooth-restored-fill, #cfe3f7);
      stroke: var(--pu-tooth-restored-stroke, #3a6ea5);
      stroke-dasharray: 1.5 1.5;
    }
    .tooth.status-absent .crown,
    .tooth.status-absent .root {
      fill: none;
      stroke: var(--pu-tooth-absent, #999);
      stroke-dasharray: 3 3;
    }
    .tooth.status-unerupted .crown,
    .tooth.status-unerupted .root {
      opacity: 0.45;
      stroke-dasharray: 4 3;
    }
    .canal.c-treat {
      stroke: var(--pu-canal-treat, #c0392b);
    }
    .canal.c-done {
      stroke: var(--pu-canal-done, #2e8b57);
    }
    .canal.c-under-body {
      stroke: var(--pu-canal-done, #2e8b57);
    }
    .canal.c-under-apex {
      stroke: var(--pu-canal-treat, #c0392b);
    }
    .quadrant-label {
      font-size: 10px;
      fill: currentColor;
      opacity: 0.6;
    }
    /* FR-009/FR-039/FR-050 — dimmed by the layer filter, but the status-* class (and its
       shape/stroke cue) stays in place, so the distinction never relies on color alone. */
    .tooth.layer-dimmed {
      opacity: 0.35;
    }
    .multi-indicator {
      font-size: 11px;
      text-anchor: middle;
      fill: var(--pu-accent-text, #7a5a2e);
      pointer-events: none;
    }
    /* FR-046 — deciduous teeth are already smaller (tooth-geometry.ts's per-position widths); this
       dotted crown outline is the non-size cue that keeps the distinction visible even at a glance. */
    .tooth.tooth-deciduous .crown {
      stroke-dasharray: 2 1.5;
    }
    /* FR-004a-c — a multi-selected tooth gets its own outline, independent of statusClass, so
       selection state is never confused with clinical state. */
    .tooth.tooth-multi-selected .crown,
    .tooth.tooth-multi-selected .root {
      stroke: var(--pu-accent-text, #7a5a2e);
      stroke-width: 2.5;
    }
  `,
})
export class ToothArchComponent {
  readonly positions = input.required<ToothPosition[]>();
  /** +1 renders crowns pointing down/roots up (upper arch); -1 the reverse (lower arch). */
  readonly dir = input.required<1 | -1>();
  /** FR-009 — purely a view filter; 'ALL' (default) shows every layer at full strength. */
  readonly layerFilter = input<'ALL' | FindingLayer>('ALL');
  /** FR-004a-c, US6 — the active multi-selection, rendered with its own outline (independent of
   * clinical statusClass). Empty when no multi-selection is active. */
  readonly selectedFdiNumbers = input<number[]>([]);

  readonly toothSelected = output<number>();
  /** FR-004a-c, US6 — drag-select: the pointer went down on this tooth (gesture start). */
  readonly toothPointerDown = output<number>();
  /** FR-004a-c, US6 — drag-select: pointer moved onto this tooth (gesture continuation). */
  readonly toothPointerEnter = output<number>();
  /** FR-020a — right-click or long-press on a tooth opens the quick-add context menu. */
  readonly toothContextMenu = output<{ fdiNumber: number; x: number; y: number }>();

  private longPressTimer: ReturnType<typeof setTimeout> | null = null;
  private static readonly LONG_PRESS_MS = 500;

  readonly renderedTeeth = computed<RenderedTooth[]>(() => {
    const dir = this.dir();
    return this.positions().map((position, index) => {
      const anatomy = toothAnatomy(position.fdiNumber);
      const ratio = CROWN_ROOT_RATIO[anatomy.toothType];
      const w = anatomy.width;
      const rootH = w * ratio.root;
      const crownD = crownPath(w, CROWN_H, ratio.cusps, dir, ratio.bump);
      const rg = rootGeometry(w, CROWN_H, rootH, anatomy.roots, dir);
      const canals = position.canals.filter((c) => !c.removed);
      const nodes = canalNodes(canals, rg, CROWN_H, rootH, dir, anatomy.roots);
      return {
        position,
        fdiNumber: position.fdiNumber,
        x: index * COLUMN_WIDTH,
        crownD,
        rootD: rg.d,
        canalLines: nodes
          .filter((n): n is Extract<typeof n, { kind: 'line' }> => n.kind === 'line')
          .map((n) => ({ d: n.d, cls: n.cls })),
        canalDots: nodes
          .filter((n): n is Extract<typeof n, { kind: 'dot' }> => n.kind === 'dot')
          .map((n) => ({ x: n.x, y: n.y, treat: n.treat })),
        statusClass: this.statusOf(position),
        deciduous: anatomy.deciduous,
        dimmed: this.isDimmed(position),
        hasMultipleFindings:
          position.currentFindings.filter((f) => f.clinicalStatus === 'ACTIVE').length >
          MULTI_FINDING_THRESHOLD,
        labelPl: anatomy.labelPl,
        ariaLabel: this.ariaLabelFor(position, anatomy.labelPl),
      };
    });
  });

  readonly quadrantLabels = computed(() => {
    const positions = this.positions();
    const dir = this.dir();
    const labels: Array<{ quadrant: number; x: number; y: number }> = [];
    let lastQuadrant: number | null = null;
    positions.forEach((position, index) => {
      const quadrant = Math.floor(position.fdiNumber / 10);
      if (quadrant !== lastQuadrant) {
        labels.push({ quadrant, x: index * COLUMN_WIDTH + COLUMN_WIDTH / 2, y: dir === 1 ? -8 : 46 });
        lastQuadrant = quadrant;
      }
    });
    return labels;
  });

  readonly viewBox = computed(() => {
    const count = Math.max(this.positions().length, 1);
    return `0 -20 ${count * COLUMN_WIDTH} 60`;
  });

  select(fdiNumber: number): void {
    this.toothSelected.emit(fdiNumber);
  }

  /** FR-052 — arrow keys move focus between adjacent teeth in the row. */
  onArrowKey(event: KeyboardEvent, index: number): void {
    const teeth = this.renderedTeeth();
    const delta = event.key === 'ArrowRight' ? 1 : -1;
    const nextIndex = Math.min(Math.max(index + delta, 0), teeth.length - 1);
    if (nextIndex === index) {
      return;
    }
    event.preventDefault();
    const svg = (event.currentTarget as SVGGElement).ownerSVGElement;
    const nextEl = svg?.querySelector<SVGGElement>(`[data-testid="tooth-${teeth[nextIndex].fdiNumber}"]`);
    nextEl?.focus();
  }

  onContextMenu(event: MouseEvent, fdiNumber: number): void {
    event.preventDefault();
    this.toothContextMenu.emit({ fdiNumber, x: event.clientX, y: event.clientY });
  }

  isMultiSelected(fdiNumber: number): boolean {
    return this.selectedFdiNumbers().includes(fdiNumber);
  }

  /** FR-004a-c, US6 — drag-select: the pointer landed on/moved onto this tooth; the parent
   * decides whether that means anything (only while a drag is active in multi-select mode). */
  onPointerEnter(fdiNumber: number): void {
    this.toothPointerEnter.emit(fdiNumber);
  }

  onPointerDown(event: PointerEvent, fdiNumber: number): void {
    this.toothPointerDown.emit(fdiNumber);
    if (event.pointerType !== 'touch') {
      return;
    }
    const { clientX, clientY } = event;
    this.longPressTimer = setTimeout(() => {
      this.toothContextMenu.emit({ fdiNumber, x: clientX, y: clientY });
    }, ToothArchComponent.LONG_PRESS_MS);
  }

  onPointerUp(): void {
    if (this.longPressTimer !== null) {
      clearTimeout(this.longPressTimer);
      this.longPressTimer = null;
    }
  }

  private statusOf(position: ToothPosition): RenderedTooth['statusClass'] {
    if (position.presence === 'EXTRACTED' || position.presence === 'CONGENITALLY_MISSING') {
      return 'absent';
    }
    if (position.presence === 'UNERUPTED') {
      return 'unerupted';
    }
    const activeFindings = position.currentFindings.filter((f) => f.clinicalStatus === 'ACTIVE');
    if (activeFindings.some((f) => f.diagnosisCatalogEntry.layer === 'DIAGNOSIS')) {
      return 'diseased';
    }
    if (activeFindings.some((f) => f.diagnosisCatalogEntry.layer === 'EXISTING_STATE')) {
      return 'restored';
    }
    return 'healthy';
  }

  /** FR-009 — a tooth is dimmed only when its shown status is entirely attributable to the
   * layer the current filter excludes (e.g. an EXISTING_STATE-only tooth under the
   * "rozpoznanie"-only filter); a DIAGNOSIS finding always keeps a tooth at full strength. */
  private isDimmed(position: ToothPosition): boolean {
    const filter = this.layerFilter();
    if (filter === 'ALL') {
      return false;
    }
    const status = this.statusOf(position);
    if (filter === 'DIAGNOSIS') {
      return status === 'restored';
    }
    return status === 'diseased';
  }

  private ariaLabelFor(position: ToothPosition, labelPl: string): string {
    const statusSummary =
      position.currentFindings.length > 0
        ? position.currentFindings.map((f) => f.diagnosisCatalogEntry.namePl).join(', ')
        : 'brak odnotowanych zmian';
    return `ząb ${position.fdiNumber}, ${labelPl}, ${statusSummary}`;
  }
}
