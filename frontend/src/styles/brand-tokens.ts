// Źródło prawdy dla audytu kontrastu i testu parzystości (research.md R5, FR-018).
// Lustrzane wobec `design/brand/_pu-tokens.scss` — token-parity.spec.ts pilnuje zgodności.
// Wartości: contracts/design-tokens.md §1.

export type ThemeName = 'light' | 'dark';
export type HexColor = `#${string}`;

export type RoleName =
  | 'bg'
  | 'surface'
  | 'surface-raised'
  | 'border'
  | 'border-strong'
  | 'text'
  | 'text-muted'
  | 'text-disabled'
  | 'accent'
  | 'accent-text'
  | 'accent-hover'
  | 'on-accent'
  | 'euc'
  | 'euc-text'
  | 'on-euc'
  | 'success'
  | 'on-success'
  | 'warning'
  | 'on-warning'
  | 'error'
  | 'on-error'
  | 'info'
  | 'on-info'
  | 'focus-ring'
  | 'focus-ring-on-accent'
  | 'tooth-healthy-fill'
  | 'tooth-healthy-stroke'
  | 'tooth-diseased-fill'
  | 'tooth-diseased-stroke'
  | 'tooth-selected-stroke'
  | 'tooth-root-fill'
  | 'tooth-restored-fill'
  | 'tooth-restored-stroke'
  | 'tooth-closed-stroke'
  | 'tooth-absent'
  | 'canal-treat'
  | 'canal-done';

export type BrandTokens = Record<RoleName, Record<ThemeName, HexColor>>;

export const brandTokens: BrandTokens = {
  bg: { light: '#FAF7F2', dark: '#1A1819' },
  surface: { light: '#FFFFFF', dark: '#2E2C2D' },
  'surface-raised': { light: '#F2EDE6', dark: '#363233' },
  border: { light: '#E6DFD5', dark: '#504C4B' },
  'border-strong': { light: '#7D746F', dark: '#8C8480' },

  text: { light: '#1F1D1E', dark: '#EAE4DC' },
  'text-muted': { light: '#5C5654', dark: '#A79E96' },
  'text-disabled': { light: '#8C8480', dark: '#7A726C' },

  accent: { light: '#CBAD89', dark: '#CBAD89' },
  'accent-text': { light: '#7A5A2E', dark: '#E3C9A6' },
  'accent-hover': { light: '#B2946E', dark: '#E3C9A6' },
  'on-accent': { light: '#1F1D1E', dark: '#1A1819' },

  euc: { light: '#3E7A72', dark: '#7FB3AA' },
  'euc-text': { light: '#2F5D57', dark: '#7FB3AA' },
  'on-euc': { light: '#FAF7F2', dark: '#1A1819' },

  success: { light: '#2E6B45', dark: '#6FBF8E' },
  'on-success': { light: '#FAF7F2', dark: '#1A1819' },
  warning: { light: '#9A5B00', dark: '#E8A33D' },
  'on-warning': { light: '#FAF7F2', dark: '#1A1819' },
  error: { light: '#A33A32', dark: '#E88178' },
  'on-error': { light: '#FAF7F2', dark: '#1A1819' },
  info: { light: '#3B5A7A', dark: '#8FB2D6' },
  'on-info': { light: '#FAF7F2', dark: '#1A1819' },

  'focus-ring': { light: '#7A5A2E', dark: '#E3C9A6' },
  'focus-ring-on-accent': { light: '#4E3A1F', dark: '#1A1819' },

  'tooth-healthy-fill': { light: '#FFFFFF', dark: '#363233' },
  'tooth-healthy-stroke': { light: '#5C5654', dark: '#A79E96' },
  'tooth-diseased-fill': { light: '#F7E3E1', dark: '#5A2B27' },
  'tooth-diseased-stroke': { light: '#A33A32', dark: '#E88178' },
  'tooth-selected-stroke': { light: '#7A5A2E', dark: '#E3C9A6' },

  // Feature 005 (odontogram z rozpoznaniami) — research.md D10.
  'tooth-root-fill': { light: '#EAE0D2', dark: '#413C3B' },
  'tooth-restored-fill': { light: '#D7E8E4', dark: '#24413E' },
  'tooth-restored-stroke': { light: '#3E7A72', dark: '#7FB3AA' },
  'tooth-closed-stroke': { light: '#2E6B45', dark: '#6FBF8E' },
  'tooth-absent': { light: '#8C8480', dark: '#7A726C' },
  'canal-treat': { light: '#A33A32', dark: '#E88178' },
  'canal-done': { light: '#2E6B45', dark: '#6FBF8E' },
};
