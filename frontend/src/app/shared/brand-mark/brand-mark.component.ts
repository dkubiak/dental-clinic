import { Component } from '@angular/core';

/**
 * Znak marki Projekt Uśmiech (FR-023) — łuk uśmiechu w obrysie kołowym. Obie kreski rysowane
 * `currentColor`, żeby znak automatycznie przyjmował kolor tekstu kontekstu, w którym stoi:
 * `on-primary` na złotym pasku nawigacji, `on-surface` na karcie logowania — bez tego złoty
 * pasek zmieniłby znak w złoto na złocie (nieczytelne). Czytelność w obu motywach wynika
 * z tego samego mechanizmu `light-dark()`, który niesie `currentColor`.
 */
@Component({
  selector: 'app-brand-mark',
  standalone: true,
  template: `
    <svg
      data-testid="brand-mark"
      role="img"
      aria-label="Projekt Uśmiech"
      width="28"
      height="28"
      viewBox="0 0 28 28"
      class="mark"
    >
      <circle class="mark-ring" cx="14" cy="14" r="12" fill="none" stroke-width="2" />
      <path class="mark-smile" d="M8 15 Q14 21 20 15" fill="none" stroke-width="2" stroke-linecap="round" />
    </svg>
  `,
  styles: `
    .mark {
      display: block;
      color: inherit;
    }
    .mark-ring {
      stroke: currentColor;
      opacity: 0.5;
    }
    .mark-smile {
      stroke: currentColor;
    }
  `,
})
export class BrandMarkComponent {}
