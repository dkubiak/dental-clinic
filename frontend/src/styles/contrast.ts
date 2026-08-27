// Współczynnik kontrastu WCAG 2.1 (sRGB) — jedyne narzędzie pomiarowe, na którym opiera
// się audyt kontrastu (contrast-audit.spec.ts). Formuła: https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio

function srgbChannelToLinear(channel8bit: number): number {
  const c = channel8bit / 255;
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function relativeLuminance(hex: string): number {
  const normalized = hex.replace('#', '');
  const r = parseInt(normalized.slice(0, 2), 16);
  const g = parseInt(normalized.slice(2, 4), 16);
  const b = parseInt(normalized.slice(4, 6), 16);
  const [rl, gl, bl] = [r, g, b].map(srgbChannelToLinear);
  return 0.2126 * rl + 0.7152 * gl + 0.0722 * bl;
}

/** Współczynnik kontrastu WCAG 2.1 między dwoma kolorami hex (`#rrggbb`), niezależny od kolejności. */
export function contrastRatio(colorA: string, colorB: string): number {
  const lA = relativeLuminance(colorA);
  const lB = relativeLuminance(colorB);
  const lighter = Math.max(lA, lB);
  const darker = Math.min(lA, lB);
  return (lighter + 0.05) / (darker + 0.05);
}
