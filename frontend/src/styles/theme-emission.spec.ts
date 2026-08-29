import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { brandTokens } from './brand-tokens';

const tokensByName = brandTokens as unknown as Record<string, { light: string; dark: string }>;

const THEME_SCSS_PATH = resolve(__dirname, '_pu-theme.scss');
const INDEX_HTML_PATH = resolve(__dirname, '../index.html');

// Mapowanie nazw tokenów systemowych Material (klucze $overrides, bez prefiksu --mat-sys-)
// na role marki — contracts/design-tokens.md §3.
const SYSTEM_TOKEN_TO_ROLE: Record<string, string> = {
  primary: 'accent',
  'on-primary': 'on-accent',
  surface: 'surface',
  'surface-container': 'surface-raised',
  background: 'bg',
  'on-surface': 'text',
  'on-surface-variant': 'text-muted',
  outline: 'border-strong',
  'outline-variant': 'border',
  error: 'error',
  'on-error': 'on-error',
  tertiary: 'euc',
};

const MANDATORY_BUTTON_OVERRIDES = [
  '--mat-button-text-label-text-color',
  '--mat-button-outlined-label-text-color',
  '--mat-button-protected-label-text-color',
];

interface ParsedThemeScss {
  overrides: Record<string, { light: string; dark: string } | { flat: string }>;
  buttonOverrides: Record<string, { light: string; dark: string } | { flat: string }>;
}

const LIGHT_DARK_RE = /light-dark\(\s*(#[0-9A-Fa-f]{6})\s*,\s*(#[0-9A-Fa-f]{6})\s*\)/i;

function parseThemeScss(source: string): ParsedThemeScss {
  const overrides: ParsedThemeScss['overrides'] = {};
  const overridesBlock = source.match(/\$overrides:\s*\(([\s\S]*?)\n\);/);
  if (overridesBlock) {
    const entryRe = /['"]?([a-z-]+)['"]?\s*:\s*(light-dark\([^)]*\)|[^,\n]+),?/gi;
    let m: RegExpExecArray | null;
    while ((m = entryRe.exec(overridesBlock[1])) !== null) {
      const key = m[1];
      const value = m[2].trim();
      const ld = value.match(LIGHT_DARK_RE);
      overrides[key] = ld ? { light: ld[1].toUpperCase(), dark: ld[2].toUpperCase() } : { flat: value };
    }
  }

  const buttonOverrides: ParsedThemeScss['buttonOverrides'] = {};
  for (const varName of MANDATORY_BUTTON_OVERRIDES) {
    const declRe = new RegExp(`${varName}\\s*:\\s*([^;]+);`);
    const m = source.match(declRe);
    if (m) {
      const value = m[1].trim();
      const ld = value.match(LIGHT_DARK_RE);
      buttonOverrides[varName] = ld ? { light: ld[1].toUpperCase(), dark: ld[2].toUpperCase() } : { flat: value };
    }
  }

  return { overrides, buttonOverrides };
}

describe('emisja motywu: $overrides, nadpisania przycisków, meta color-scheme (research.md R2, R3)', () => {
  it('_pu-theme.scss istnieje i definiuje mapę $overrides', () => {
    const source = readFileSync(THEME_SCSS_PATH, 'utf-8');
    expect(source).toMatch(/\$overrides:\s*\(/);
  });

  it('każda wartość w $overrides jest zapisana jako light-dark(#jasny, #ciemny) — płaska wartość zabija zmienność motywu (R2)', () => {
    const source = readFileSync(THEME_SCSS_PATH, 'utf-8');
    const { overrides } = parseThemeScss(source);
    expect(Object.keys(overrides).length, '$overrides jest puste').toBeGreaterThan(0);
    for (const [key, value] of Object.entries(overrides)) {
      expect('flat' in value, `klucz '${key}' ma płaską wartość '${'flat' in value ? value.flat : ''}' zamiast light-dark()`).toBe(false);
    }
  });

  it('trzy obowiązkowe nadpisania komponentowe przycisków są obecne jako light-dark() (R3)', () => {
    const source = readFileSync(THEME_SCSS_PATH, 'utf-8');
    const { buttonOverrides } = parseThemeScss(source);
    for (const varName of MANDATORY_BUTTON_OVERRIDES) {
      expect(buttonOverrides[varName], `brak deklaracji ${varName}`).toBeDefined();
      expect('flat' in buttonOverrides[varName], `${varName} ma płaską wartość zamiast light-dark()`).toBe(false);
    }
  });

  it('wartości jasny/ciemny w $overrides zgadzają się z odpowiadającymi rolami w brand-tokens.ts', () => {
    const source = readFileSync(THEME_SCSS_PATH, 'utf-8');
    const { overrides } = parseThemeScss(source);
    for (const [systemKey, role] of Object.entries(SYSTEM_TOKEN_TO_ROLE)) {
      const entry = overrides[systemKey];
      expect(entry, `brak klucza '${systemKey}' w $overrides (rola '${role}')`).toBeDefined();
      if (entry && 'light' in entry) {
        expect(entry.light, `${systemKey} → ${role} (jasny)`).toBe(tokensByName[role].light.toUpperCase());
        expect(entry.dark, `${systemKey} → ${role} (ciemny)`).toBe(tokensByName[role].dark.toUpperCase());
      }
    }
  });

  it('nadpisania przycisków zgadzają się z rolą accent-text w brand-tokens.ts', () => {
    const source = readFileSync(THEME_SCSS_PATH, 'utf-8');
    const { buttonOverrides } = parseThemeScss(source);
    for (const varName of MANDATORY_BUTTON_OVERRIDES) {
      const entry = buttonOverrides[varName];
      if (entry && 'light' in entry) {
        expect(entry.light, `${varName} (jasny)`).toBe(tokensByName['accent-text'].light.toUpperCase());
        expect(entry.dark, `${varName} (ciemny)`).toBe(tokensByName['accent-text'].dark.toUpperCase());
      }
    }
  });

  it('index.html deklaruje <meta name="color-scheme" content="light dark">', () => {
    const html = readFileSync(INDEX_HTML_PATH, 'utf-8');
    expect(html).toMatch(/<meta\s+name=["']color-scheme["']\s+content=["']light dark["']\s*\/?>/i);
  });
});
