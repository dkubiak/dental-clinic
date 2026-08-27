import { describe, expect, it } from 'vitest';
import { contrastRatio } from './contrast';
import { brandTokens } from './brand-tokens';
import { assertPairAllowed, contrastPairs, type ContrastPair, type ThemeName } from './contrast-pairs';

function describePair(pair: ContrastPair, theme: ThemeName): string {
  const fg = brandTokens[pair.foreground][theme];
  const bg = brandTokens[pair.background][theme];
  return `${pair.foreground} ${fg} na ${pair.background} ${bg} (motyw ${theme === 'light' ? 'jasny' : 'ciemny'})`;
}

describe('audyt kontrastu (FR-017, FR-018)', () => {
  for (const pair of contrastPairs) {
    const themes: ThemeName[] = pair.themes ?? ['light', 'dark'];
    for (const theme of themes) {
      it(`${pair.foreground} na ${pair.background} osiąga próg ${pair.minRatio}:1 (${theme})`, () => {
        const fg = brandTokens[pair.foreground][theme];
        const bg = brandTokens[pair.background][theme];
        const ratio = contrastRatio(fg, bg);
        expect(ratio, describePair(pair, theme)).toBeGreaterThanOrEqual(pair.minRatio);
      });
    }
  }

  // A4 — para `accent` jako tekst nad jasnym tłem MUSI zostać odrzucona, nawet gdyby ktoś
  // dopisał ją do contrast-pairs.ts (contracts/theme-preference.md §4, contracts/design-tokens.md §2)
  it('odrzuca parę accent jako tekst nad jasnym tłem, gdyby ktoś ją dopisał do listy (FR-004)', () => {
    const forbidden: ContrastPair = {
      foreground: 'accent',
      background: 'bg',
      minRatio: 4.5,
      themes: ['light'],
    };
    expect(() => assertPairAllowed(forbidden)).toThrow(/accent/);
  });

  it('nie odrzuca accent jako tekstu nad ciemnym tłem (dozwolone przez FR-004)', () => {
    const allowed: ContrastPair = {
      foreground: 'accent',
      background: 'bg',
      minRatio: 4.5,
      themes: ['dark'],
    };
    expect(() => assertPairAllowed(allowed)).not.toThrow();
  });
});
