import { Component, computed, input, output } from '@angular/core';
import { ToothPosition } from '../patients.models';
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
  labelPl: string;
  ariaLabel: string;
}

const COLUMN_WIDTH = 40;
const CROWN_H = 26;

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
          [attr.data-testid]="'tooth-' + tooth.fdiNumber"
          [attr.tabindex]="0"
          [attr.role]="'button'"
          [attr.aria-label]="tooth.ariaLabel"
          (click)="select(tooth.fdiNumber)"
          (keydown.enter)="select(tooth.fdiNumber)"
          (keydown.arrowright)="onArrowKey($any($event), i)"
          (keydown.arrowleft)="onArrowKey($any($event), i)"
          (contextmenu)="onContextMenu($event, tooth.fdiNumber)"
          (pointerdown)="onPointerDown($event, tooth.fdiNumber)"
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
    .tooth.status-diseased .crown {
      fill: var(--pu-tooth-diseased-fill, #f6cccc);
      stroke: var(--pu-tooth-diseased-stroke, #b33);
    }
    .tooth.status-restored .crown {
      fill: var(--pu-tooth-restored-fill, #cfe3f7);
      stroke: var(--pu-tooth-restored-stroke, #3a6ea5);
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
  `,
})
export class ToothArchComponent {
  readonly positions = input.required<ToothPosition[]>();
  /** +1 renders crowns pointing down/roots up (upper arch); -1 the reverse (lower arch). */
  readonly dir = input.required<1 | -1>();

  readonly toothSelected = output<number>();
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

  onPointerDown(event: PointerEvent, fdiNumber: number): void {
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

  private ariaLabelFor(position: ToothPosition, labelPl: string): string {
    const statusSummary =
      position.currentFindings.length > 0
        ? position.currentFindings.map((f) => f.diagnosisCatalogEntry.namePl).join(', ')
        : 'brak odnotowanych zmian';
    return `ząb ${position.fdiNumber}, ${labelPl}, ${statusSummary}`;
  }
}
