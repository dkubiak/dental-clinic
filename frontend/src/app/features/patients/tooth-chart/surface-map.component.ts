import { Component, computed, input, output } from '@angular/core';
import { ToothSurface } from '../patients.models';
import { surfaceNamePl, toothAnatomy, zoneDefs } from './tooth-geometry';

interface RenderedZone {
  surface: ToothSurface;
  d: string;
  namePl: string;
  selected: boolean;
  hasEntry: boolean;
  letter: string;
}

const ZONE_LETTERS: Record<ToothSurface, string> = {
  MESIAL: 'M',
  DISTAL: 'D',
  VESTIBULAR: 'B',
  LINGUAL_PALATAL: 'L',
  OCCLUSAL_INCISAL: 'O',
};

/**
 * FR-024/FR-026/FR-029/FR-029a — one instance is used both at the middle-strip size on the main
 * diagram (no letters, US2) and at the enlarged size inside tooth-detail-panel.component.ts
 * (letters shown, `showLabels`). Offers an incisal surface for incisors/canines and an occlusal
 * surface for premolars/molars — never both, since OCCLUSAL_INCISAL is a single surface whose
 * display name (not existence) depends on tooth type (research.md D11, tooth-geometry.ts).
 */
@Component({
  selector: 'app-surface-map',
  standalone: true,
  template: `
    <svg
      [attr.viewBox]="viewBox()"
      class="surface-map"
      [class.with-labels]="showLabels()"
      role="img"
      [attr.aria-label]="'Mapa powierzchni zęba ' + fdiNumber()"
    >
      @for (zone of zones(); track zone.surface) {
        <g
          class="zone"
          [class.selected]="zone.selected"
          [class.has-entry]="zone.hasEntry"
          [attr.data-testid]="'surface-zone-' + zone.surface"
          [attr.tabindex]="0"
          role="button"
          [attr.aria-label]="zone.namePl + (zone.selected ? ' (zaznaczona)' : '')"
          (click)="toggle(zone.surface)"
          (keydown.enter)="toggle(zone.surface)"
          (contextmenu)="onContextMenu($event, zone.surface)"
          (pointerdown)="onPointerDown($event, zone.surface)"
          (pointerup)="onPointerUp()"
          (pointerleave)="onPointerUp()"
        >
          <title>{{ zone.namePl }}</title>
          <path [attr.d]="zone.d" class="zone-shape" />
          @if (showLabels()) {
            <text class="zone-label">{{ zone.letter }}</text>
          }
        </g>
      }
    </svg>
  `,
  styles: `
    .surface-map {
      width: 100%;
      max-width: 160px;
    }
    .zone {
      cursor: pointer;
    }
    .zone-shape {
      fill: var(--pu-surface-raised, #f2ede6);
      stroke: var(--pu-border-strong, #7d746f);
      stroke-width: 1;
    }
    .zone.selected .zone-shape {
      fill: var(--pu-accent, #cbad89);
      stroke: var(--pu-accent-text, #7a5a2e);
      stroke-width: 2.5;
    }
    .zone.has-entry .zone-shape {
      fill: var(--pu-tooth-diseased-fill, #f7e3e1);
    }
    .zone-label {
      font-size: 9px;
      text-anchor: middle;
      dominant-baseline: middle;
      fill: var(--pu-text-muted, #5c5654);
      pointer-events: none;
    }
  `,
})
export class SurfaceMapComponent {
  readonly fdiNumber = input.required<number>();
  readonly size = input<number>(80);
  readonly selectedSurfaces = input<ToothSurface[]>([]);
  readonly existingSurfaces = input<ToothSurface[]>([]);
  /** FR-029a — letters only in the enlarged detail-panel map, never on the main diagram. */
  readonly showLabels = input<boolean>(false);

  readonly surfaceToggled = output<ToothSurface>();
  /** FR-020a, T066 — right-click or long-press on a surface zone opens the quick-add context
   * menu pre-targeted at this surface, same as right-clicking the tooth itself in
   * tooth-arch.component.ts. */
  readonly zoneContextMenu = output<{ surface: ToothSurface; x: number; y: number }>();

  private longPressTimer: ReturnType<typeof setTimeout> | null = null;
  private static readonly LONG_PRESS_MS = 500;

  readonly zones = computed<RenderedZone[]>(() => {
    const fdiNumber = this.fdiNumber();
    const anatomy = toothAnatomy(fdiNumber);
    const defs = zoneDefs(this.size(), anatomy.right);
    const selected = new Set(this.selectedSurfaces());
    const existing = new Set(this.existingSurfaces());
    return defs.map((zone) => ({
      surface: zone.surface,
      d: zone.d,
      namePl: surfaceNamePl(fdiNumber, zone.surface),
      selected: selected.has(zone.surface),
      hasEntry: existing.has(zone.surface),
      letter:
        zone.surface === 'OCCLUSAL_INCISAL'
          ? anatomy.anterior
            ? 'I'
            : 'O'
          : ZONE_LETTERS[zone.surface],
    }));
  });

  readonly viewBox = computed(() => {
    const half = this.size() / 2;
    return `${-half} ${-half} ${this.size()} ${this.size()}`;
  });

  toggle(surface: ToothSurface): void {
    this.surfaceToggled.emit(surface);
  }

  onContextMenu(event: MouseEvent, surface: ToothSurface): void {
    event.preventDefault();
    this.zoneContextMenu.emit({ surface, x: event.clientX, y: event.clientY });
  }

  onPointerDown(event: PointerEvent, surface: ToothSurface): void {
    if (event.pointerType !== 'touch') {
      return;
    }
    const { clientX, clientY } = event;
    this.longPressTimer = setTimeout(() => {
      this.zoneContextMenu.emit({ surface, x: clientX, y: clientY });
    }, SurfaceMapComponent.LONG_PRESS_MS);
  }

  onPointerUp(): void {
    if (this.longPressTimer !== null) {
      clearTimeout(this.longPressTimer);
      this.longPressTimer = null;
    }
  }
}
