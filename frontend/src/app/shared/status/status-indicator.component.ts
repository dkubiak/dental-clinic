import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type StatusType = 'success' | 'warning' | 'error' | 'info';

const ICON_BY_TYPE: Record<StatusType, string> = {
  success: 'check_circle',
  warning: 'warning',
  error: 'error',
  info: 'info',
};

/**
 * Shared status/message component (US3, contracts/design-tokens.md "Kontrapunkt i kolory
 * funkcyjne") — every type is carried by an icon and a text label, never by color alone
 * (FR-019), so meaning survives colorblindness and grayscale printing.
 *
 * T050/T051 verification (deuteranopia/protanopia simulation on the real token values, not just
 * a contrast-ratio calculation — WCAG luminance contrast doesn't capture hue confusion): under
 * both simulations, `success` and `error` collapse to nearly the same dark olive/brown — proving
 * the icon is load-bearing, not decorative. `warning` vs `accent` in dark mode also converge
 * toward a similar yellow-khaki hue (the design docs' own "two warm, similar shades" warning,
 * contracts/design-tokens.md); they stay distinguishable mainly by the accent being a filled
 * pill/button (a different shape/context) rather than by hue alone. Don't rely on color-only
 * cues when adding new status types here.
 */
@Component({
  selector: 'app-status-indicator',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <span class="status" [class]="'status-' + type()" role="status">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      <span class="message">{{ message() }}</span>
    </span>
  `,
  styles: `
    .status {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .status-success {
      color: var(--pu-success);
    }
    .status-warning {
      color: var(--pu-warning);
    }
    .status-error {
      color: var(--mat-sys-error);
    }
    .status-info {
      color: var(--pu-info);
    }
    mat-icon {
      flex: none;
    }
  `,
})
export class StatusIndicatorComponent {
  readonly type = input.required<StatusType>();
  readonly message = input.required<string>();

  protected readonly icon = computed(() => ICON_BY_TYPE[this.type()]);
}
