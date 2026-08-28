import { Component, inject } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ThemeService } from './theme.service';

/**
 * Przełącznik jasny/ciemny (contracts/theme-preference.md §1) — obecny na każdym ekranie,
 * także przed zalogowaniem (FR-008). Nazwa dostępna komunikuje zarówno bieżący stan, jak
 * i skutek aktywacji (FR-015), więc zmienia się po każdym przełączeniu.
 */
@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [MatIconButton, MatIconModule, MatTooltipModule],
  template: `
    <button
      mat-icon-button
      type="button"
      data-testid="theme-toggle"
      [attr.aria-label]="label()"
      [matTooltip]="label()"
      (click)="themeService.toggle()"
    >
      <mat-icon>{{ icon() }}</mat-icon>
    </button>
  `,
  styles: `
    button {
      color: inherit;
    }

    button:focus-visible {
      outline: 2px solid var(--pu-focus-ring, var(--mat-sys-primary));
      outline-offset: 2px;
    }
  `,
})
export class ThemeToggleComponent {
  protected readonly themeService = inject(ThemeService);

  protected readonly icon = () => (this.themeService.resolved() === 'dark' ? 'dark_mode' : 'light_mode');

  protected readonly label = () =>
    this.themeService.resolved() === 'dark'
      ? 'Motyw ciemny włączony. Kliknij, aby przełączyć na jasny.'
      : 'Motyw jasny włączony. Kliknij, aby przełączyć na ciemny.';
}
