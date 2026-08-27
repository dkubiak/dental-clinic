import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

// Dwanaście ekranów aplikacji personelu — jedyne miejsca, gdzie kolor mógłby wyciec poza
// system tokenów (plan.md, Project Structure). Ścieżki względem `frontend/src/`.
const COMPONENT_FILES = [
  'app/core/shell/app-shell.component.ts',
  'app/features/auth/login/login.component.ts',
  'app/features/auth/login/mfa-challenge.component.ts',
  'app/features/auth/password-reset/password-reset-request.component.ts',
  'app/features/auth/password-reset/password-reset-confirm.component.ts',
  'app/features/home/role-home.component.ts',
  'app/features/patients/patient-search/patient-search.component.ts',
  'app/features/patients/patient-create/patient-create.component.ts',
  'app/features/patients/patient-detail/patient-detail.component.ts',
  'app/features/patients/tooth-chart/tooth-chart.component.ts',
  'app/features/admin/accounts/accounts.component.ts',
  'app/features/admin/audit-log/audit-log.component.ts',
];

const NAMED_CSS_COLORS = [
  'white',
  'black',
  'red',
  'green',
  'blue',
  'yellow',
  'orange',
  'purple',
  'pink',
  'gray',
  'grey',
  'cyan',
  'magenta',
  'brown',
  'navy',
  'teal',
  'maroon',
  'olive',
  'lime',
  'aqua',
  'silver',
  'gold',
  'indigo',
  'violet',
  'coral',
  'salmon',
  'khaki',
  'crimson',
  'orchid',
  'plum',
  'tan',
  'beige',
  'azure',
  'ivory',
  'lavender',
  'transparent',
];

const COLOR_PROPERTY_RE = new RegExp(
  `\\b(color|background|background-color|border(?:-\\w+)?-color|fill|stroke|outline-color|box-shadow)\\s*:\\s*(?:` +
    `#[0-9A-Fa-f]{3,8}\\b` + // #rgb / #rrggbb / #rrggbbaa
    `|rgba?\\([^)]*\\)` + // rgb()/rgba()
    `|hsla?\\([^)]*\\)` + // hsl()/hsla()
    `|(?:${NAMED_CSS_COLORS.join('|')})\\b` + // nazwa CSS
    `|var\\(\\s*--mat-sys-[a-z-]+\\s*,\\s*#[0-9A-Fa-f]{3,8}` + // var(--mat-sys-*, #fallback)
    `)`,
  'gi',
);

function findLiteralColorViolations(source: string): string[] {
  const violations: string[] = [];
  const lines = source.split('\n');
  lines.forEach((line, i) => {
    COLOR_PROPERTY_RE.lastIndex = 0;
    if (COLOR_PROPERTY_RE.test(line)) {
      violations.push(`  linia ${i + 1}: ${line.trim()}`);
    }
  });
  return violations;
}

describe('brak literalnych kolorów w komponentach (FR-001, asercja A6)', () => {
  for (const relPath of COMPONENT_FILES) {
    it(`${relPath} nie zawiera literalnej wartości koloru ani var(--mat-sys-*, #fallback)`, () => {
      const content = readFileSync(resolve(__dirname, '..', relPath), 'utf-8');
      const violations = findLiteralColorViolations(content);
      expect(violations, `Literalne kolory w ${relPath}:\n${violations.join('\n')}`).toEqual([]);
    });
  }
});
