// Lista par podlegających audytowi kontrastu (contracts/design-tokens.md §2, FR-017).
// Pary celowo NIEobjęte audytem (border wobec bg, wypełnienia zęba wobec bg, wypełnienia
// zdrowy/chory wobec siebie) nie są tu wymienione — zob. §2 „Pary celowo NIEobjęte audytem".

import type { RoleName } from './brand-tokens';

export type ThemeName = 'light' | 'dark';

export interface ContrastPair {
  foreground: RoleName;
  background: RoleName;
  minRatio: 4.5 | 3.0;
  themes?: ThemeName[];
}

// FR-004: złoto marki (`accent`) jest wyłącznie płaszczyzną — jako tekst nad jasnym tłem
// daje 1.99:1, poniżej progu AA. Audyt MUSI odrzucić taką parę, nawet gdyby ktoś ją dopisał
// poniżej (contracts/theme-preference.md §4, asercja A4).
const FORBIDDEN_FOREGROUND_ON_LIGHT: ReadonlySet<RoleName> = new Set(['accent']);

export function assertPairAllowed(pair: ContrastPair): void {
  const themes = pair.themes ?? ['light', 'dark'];
  if (FORBIDDEN_FOREGROUND_ON_LIGHT.has(pair.foreground) && themes.includes('light')) {
    throw new Error(
      `Rola '${pair.foreground}' nie może być użyta jako tekst nad jasnym tłem (FR-004) — ` +
        `odrzucona para ${pair.foreground}/${pair.background}`,
    );
  }
}

export const contrastPairs: ContrastPair[] = [
  // Próg 4.5:1 — tekst podstawowy
  { foreground: 'text', background: 'bg', minRatio: 4.5 },
  { foreground: 'text', background: 'surface', minRatio: 4.5 },
  { foreground: 'text-muted', background: 'bg', minRatio: 4.5 },
  { foreground: 'text-muted', background: 'surface', minRatio: 4.5 },
  { foreground: 'accent-text', background: 'bg', minRatio: 4.5 },
  { foreground: 'accent-text', background: 'surface', minRatio: 4.5 },
  { foreground: 'on-accent', background: 'accent', minRatio: 4.5 },
  { foreground: 'euc-text', background: 'bg', minRatio: 4.5 },
  { foreground: 'success', background: 'bg', minRatio: 4.5 },
  { foreground: 'warning', background: 'bg', minRatio: 4.5 },
  { foreground: 'error', background: 'bg', minRatio: 4.5 },
  { foreground: 'info', background: 'bg', minRatio: 4.5 },

  // Próg 3:1 — elementy interfejsu, duży tekst, obrysy
  { foreground: 'border-strong', background: 'bg', minRatio: 3.0 },
  { foreground: 'border-strong', background: 'surface', minRatio: 3.0 },
  { foreground: 'focus-ring', background: 'bg', minRatio: 3.0 },
  { foreground: 'focus-ring', background: 'surface', minRatio: 3.0 },
  { foreground: 'focus-ring-on-accent', background: 'accent', minRatio: 3.0 },
  { foreground: 'text-disabled', background: 'bg', minRatio: 3.0 },
  { foreground: 'tooth-healthy-stroke', background: 'tooth-healthy-fill', minRatio: 3.0 },
  { foreground: 'tooth-diseased-stroke', background: 'tooth-diseased-fill', minRatio: 3.0 },
  { foreground: 'tooth-selected-stroke', background: 'tooth-healthy-fill', minRatio: 3.0 },
];

contrastPairs.forEach(assertPairAllowed);
