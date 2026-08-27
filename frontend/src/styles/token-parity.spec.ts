import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { brandTokens } from './brand-tokens';

const tokensByName = brandTokens as unknown as Record<string, { light: string; dark: string }>;
const SCSS_PATH = resolve(__dirname, '../../../design/brand/_pu-tokens.scss');

/**
 * `_pu-tokens.scss` to dokument referencyjny (design/brand/), nie jest importowany przez
 * aplikację — źródłem prawdy dla runtime jest `brand-tokens.ts` (research.md R5); ten test
 * pilnuje, żeby oba nie rozjechały się. Wartości ról bywają zapisane pośrednio, jako
 * interpolacja zmiennej SCSS (`#{$pu-gold-800}`), więc parser najpierw rozwiązuje wszystkie
 * `$zmienna: #hex` z całego pliku, dopiero potem czyta bloki `:root` / `[data-theme='dark']`
 * licząc głębokość nawiasów klamrowych — `[^}]*` nie wystarcza, bo `#{...}` też zawiera `}`.
 */
function parseScssVariables(source: string): Record<string, string> {
  const vars: Record<string, string> = {};
  const re = /\$([a-zA-Z0-9-]+):\s*(#[0-9A-Fa-f]{6})/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(source)) !== null) {
    vars[m[1]] = m[2].toUpperCase();
  }
  return vars;
}

function extractBalancedBlock(source: string, selectorRe: RegExp): string {
  const m = selectorRe.exec(source);
  if (!m) {
    throw new Error(`Nie znaleziono selektora ${selectorRe} w _pu-tokens.scss`);
  }
  let i = m.index + m[0].length;
  while (source[i] !== '{') i++;
  const start = i;
  let depth = 0;
  for (; i < source.length; i++) {
    if (source[i] === '{') depth++;
    else if (source[i] === '}') {
      depth--;
      if (depth === 0) return source.slice(start + 1, i);
    }
  }
  throw new Error(`Nierówne nawiasy klamrowe dla ${selectorRe}`);
}

function parseRoleBlock(source: string, selectorRe: RegExp, vars: Record<string, string>): Record<string, string> {
  const block = extractBalancedBlock(source, selectorRe);
  const roles: Record<string, string> = {};
  const declRe = /--pu-([a-z-]+):\s*(?:#\{\$([a-zA-Z0-9-]+)\}|(#[0-9A-Fa-f]{6}))\s*;/g;
  let m: RegExpExecArray | null;
  while ((m = declRe.exec(block)) !== null) {
    const role = m[1];
    const hex = m[2] ? vars[m[2]] : m[3]?.toUpperCase();
    if (!hex) {
      throw new Error(`Nie udało się rozwiązać wartości dla roli '${role}' (zmienna '${m[2]}')`);
    }
    roles[role] = hex;
  }
  return roles;
}

describe('parzystość tokenów: _pu-tokens.scss ↔ brand-tokens.ts (FR-002, FR-007)', () => {
  const source = readFileSync(SCSS_PATH, 'utf-8');
  const vars = parseScssVariables(source);
  const lightRoles = parseRoleBlock(source, /:root/, vars);
  const darkRoles = parseRoleBlock(source, /\[data-theme='dark'\]/, vars);

  it('parser czyta niepustą liczbę ról z obu bloków (kontrola nad samym testem)', () => {
    expect(Object.keys(lightRoles).length).toBeGreaterThan(0);
    expect(Object.keys(darkRoles).length).toBeGreaterThan(0);
  });

  it('każda rola z brand-tokens.ts ma wartość w motywie jasnym i ciemnym (A1, FR-007)', () => {
    for (const [role, values] of Object.entries(brandTokens)) {
      expect(values.light, `rola '${role}' bez wartości light`).toMatch(/^#[0-9A-Fa-f]{6}$/);
      expect(values.dark, `rola '${role}' bez wartości dark`).toMatch(/^#[0-9A-Fa-f]{6}$/);
    }
  });

  it('role obecne w _pu-tokens.scss (jasny) mają identyczną wartość w brand-tokens.ts (A5)', () => {
    for (const [role, hex] of Object.entries(lightRoles)) {
      expect(tokensByName[role], `rola '${role}' jest w _pu-tokens.scss, ale nie w brand-tokens.ts`).toBeDefined();
      expect(tokensByName[role].light.toUpperCase(), `rola '${role}' (jasny)`).toBe(hex);
    }
  });

  it('role obecne w _pu-tokens.scss (ciemny) mają identyczną wartość w brand-tokens.ts (A5)', () => {
    for (const [role, hex] of Object.entries(darkRoles)) {
      expect(tokensByName[role], `rola '${role}' jest w _pu-tokens.scss, ale nie w brand-tokens.ts`).toBeDefined();
      expect(tokensByName[role].dark.toUpperCase(), `rola '${role}' (ciemny)`).toBe(hex);
    }
  });

  it('_pu-tokens.scss definiuje te same role w obu blokach', () => {
    const lightNames = Object.keys(lightRoles).sort();
    const darkNames = Object.keys(darkRoles).sort();
    expect(darkNames).toEqual(lightNames);
  });
});
