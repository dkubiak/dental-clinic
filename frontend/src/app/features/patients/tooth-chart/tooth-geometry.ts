/**
 * Pure functions ported from mockup/odontogram-mockup.html's `meta()`, `crownPath()`,
 * `rootGeometry()`, `canalNodes()`, and `zoneDefs()` (research.md D11) — procedural, per-tooth-type
 * SVG geometry parameterized by cusp/root count rather than fixed per-tooth artwork. No Angular
 * dependency; consumed by tooth-arch.component.ts and surface-map.component.ts.
 */

export type ToothTypeName = 'INCISOR' | 'CANINE' | 'PREMOLAR' | 'MOLAR';
export type ToothSurfaceName = 'MESIAL' | 'DISTAL' | 'VESTIBULAR' | 'LINGUAL_PALATAL' | 'OCCLUSAL_INCISAL';

/** All five FR-024 surfaces exist for every tooth type — only OCCLUSAL_INCISAL's display name
 * (sieczna vs. żująca) depends on tooth type (FR-024/FR-025). */
export const ALL_SURFACES: readonly ToothSurfaceName[] = [
  'MESIAL',
  'DISTAL',
  'VESTIBULAR',
  'LINGUAL_PALATAL',
  'OCCLUSAL_INCISAL',
];

export interface ToothAnatomy {
  fdiNumber: number;
  quadrant: number;
  position: number;
  deciduous: boolean;
  upper: boolean;
  right: boolean;
  toothType: ToothTypeName;
  anterior: boolean;
  roots: number;
  width: number;
  namePl: string;
  quadrantLabelPl: string;
  labelPl: string;
}

const POS_NAME_PL: Record<number, string> = {
  1: 'siekacz przyśrodkowy',
  2: 'siekacz boczny',
  3: 'kieł',
  4: 'pierwszy przedtrzonowiec',
  5: 'drugi przedtrzonowiec',
  6: 'pierwszy trzonowiec',
  7: 'drugi trzonowiec',
  8: 'trzeci trzonowiec (ząb mądrości)',
};

const POS_NAME_DEC_PL: Record<number, string> = {
  1: 'siekacz przyśrodkowy mleczny',
  2: 'siekacz boczny mleczny',
  3: 'kieł mleczny',
  4: 'pierwszy trzonowiec mleczny',
  5: 'drugi trzonowiec mleczny',
};

const WIDTH_PERM_UPPER: Record<number, number> = { 1: 23, 2: 19, 3: 20, 4: 21, 5: 21, 6: 27, 7: 26, 8: 24 };
const WIDTH_PERM_LOWER: Record<number, number> = { 1: 16, 2: 17, 3: 19, 4: 20, 5: 21, 6: 27, 7: 26, 8: 24 };
const WIDTH_DEC_UPPER: Record<number, number> = { 1: 19, 2: 17, 3: 18, 4: 23, 5: 25 };
const WIDTH_DEC_LOWER: Record<number, number> = { 1: 15, 2: 17, 3: 18, 4: 22, 5: 24 };

/** Ported from the mockup's `meta(fdi)` — FDI/ISO 3950 -> anatomical parameters. */
export function toothAnatomy(fdiNumber: number): ToothAnatomy {
  const quadrant = Math.floor(fdiNumber / 10);
  const position = fdiNumber % 10;
  const deciduous = quadrant >= 5;
  const upper = quadrant === 1 || quadrant === 2 || quadrant === 5 || quadrant === 6;
  const right = quadrant === 1 || quadrant === 4 || quadrant === 5 || quadrant === 8;
  const toothType: ToothTypeName = deciduous
    ? position <= 2
      ? 'INCISOR'
      : position === 3
        ? 'CANINE'
        : 'MOLAR'
    : position <= 2
      ? 'INCISOR'
      : position === 3
        ? 'CANINE'
        : position <= 5
          ? 'PREMOLAR'
          : 'MOLAR';
  const anterior = position <= 3;

  let roots: number;
  if (deciduous) {
    roots = position <= 3 ? 1 : upper ? 3 : 2;
  } else if (position <= 3) {
    roots = 1;
  } else if (position <= 5) {
    roots = upper && position === 4 ? 2 : 1;
  } else {
    roots = upper ? 3 : 2;
  }

  const widths = deciduous ? (upper ? WIDTH_DEC_UPPER : WIDTH_DEC_LOWER) : upper ? WIDTH_PERM_UPPER : WIDTH_PERM_LOWER;
  const namePl = (deciduous ? POS_NAME_DEC_PL : POS_NAME_PL)[position];
  const quadrantLabelPl = (upper ? 'górny ' : 'dolny ') + (right ? 'prawy' : 'lewy');

  return {
    fdiNumber,
    quadrant,
    position,
    deciduous,
    upper,
    right,
    toothType,
    anterior,
    roots,
    width: widths[position],
    namePl,
    quadrantLabelPl,
    labelPl: `${namePl} ${quadrantLabelPl}`,
  };
}

/** FR-025 (podniebienna/językowa, wargowa/policzkowa) + FR-024 (sieczna/żująca). */
export function surfaceNamePl(fdiNumber: number, surface: ToothSurfaceName): string {
  const anatomy = toothAnatomy(fdiNumber);
  switch (surface) {
    case 'MESIAL':
      return 'mezjalna';
    case 'DISTAL':
      return 'dystalna';
    case 'VESTIBULAR':
      return anatomy.anterior ? 'wargowa' : 'policzkowa';
    case 'LINGUAL_PALATAL':
      return anatomy.upper ? 'podniebienna' : 'językowa';
    case 'OCCLUSAL_INCISAL':
      return anatomy.anterior ? 'sieczna' : 'żująca';
  }
}

export interface CrownRootRatio {
  crown: number;
  root: number;
  cusps: number;
  bump: number;
}

/** Ported from the mockup's `RATIO` table. */
export const CROWN_ROOT_RATIO: Record<ToothTypeName, CrownRootRatio> = {
  INCISOR: { crown: 0.72, root: 1.1, cusps: 1, bump: 0.03 },
  CANINE: { crown: 0.78, root: 1.3, cusps: 1, bump: 0.09 },
  PREMOLAR: { crown: 0.7, root: 1.1, cusps: 2, bump: 0.08 },
  MOLAR: { crown: 0.58, root: 0.92, cusps: 3, bump: 0.09 },
};

const round2 = (n: number): number => Math.round(n * 100) / 100;

/** Ported verbatim from the mockup's `crownPath()`. `dir` is +1 (upper arch) or -1 (lower arch). */
export function crownPath(w: number, h: number, cusps: number, dir: number, bump: number): string {
  const Y = (v: number): number => round2(dir * v);
  const nk = w * 0.4;
  const mx = w * 0.5;
  const ex = w / 2 - w * 0.1;
  let d = `M ${round2(-nk)} ${Y(h)}`;
  d += ` C ${round2(-mx)} ${Y(h * 0.74)}, ${round2(-mx)} ${Y(h * 0.28)}, ${round2(-ex)} ${Y(h * 0.05)}`;
  const seg = (2 * ex) / cusps;
  let cx = -ex;
  for (let i = 0; i < cusps; i++) {
    const nx = cx + seg;
    d += ` Q ${round2(cx + seg / 2)} ${Y(-h * bump)}, ${round2(nx)} ${Y(h * 0.05)}`;
    cx = nx;
  }
  d += ` C ${round2(mx)} ${Y(h * 0.28)}, ${round2(mx)} ${Y(h * 0.74)}, ${round2(nk)} ${Y(h)} Z`;
  return d;
}

export interface RootGeometry {
  d: string;
  centers: number[];
  tips: number[];
  slot: number;
}

/** Ported verbatim from the mockup's `rootGeometry()`. */
export function rootGeometry(w: number, crownH: number, rootH: number, n: number, dir: number): RootGeometry {
  const Y = (v: number): number => round2(dir * v);
  const CW = w * 0.8;
  const slot = CW / n;
  const yN = crownH;
  const yA = crownH + rootH;
  const yF = crownH + rootH * 0.34;
  const splay = n === 1 ? 1 : 1.18;
  const centers: number[] = [];
  const tips: number[] = [];
  for (let i = 0; i < n; i++) {
    const c = -CW / 2 + slot * (i + 0.5);
    centers.push(c);
    tips.push(c * splay);
  }
  const aw = Math.max(Math.min(slot * 0.22, w * 0.07), w * 0.035);
  let d = `M ${round2(-CW / 2)} ${Y(yN)}`;
  let fromX = -CW / 2;
  let fromY = yN;
  for (let i = 0; i < n; i++) {
    const t = tips[i];
    d += ` C ${round2(fromX * 1.02)} ${Y(fromY + (yA - fromY) * 0.42)}, ${round2(t - aw * 1.9)} ${Y(yA - rootH * 0.26)}, ${round2(t - aw)} ${Y(yA)}`;
    d += ` Q ${round2(t)} ${Y(yA + rootH * 0.05)}, ${round2(t + aw)} ${Y(yA)}`;
    if (i < n - 1) {
      const fx = (centers[i] + centers[i + 1]) / 2;
      d += ` C ${round2(t + aw * 1.9)} ${Y(yA - rootH * 0.26)}, ${round2(fx)} ${Y(yF + rootH * 0.24)}, ${round2(fx)} ${Y(yF)}`;
      fromX = fx;
      fromY = yF;
    } else {
      d += ` C ${round2(t + aw * 1.9)} ${Y(yA - rootH * 0.26)}, ${round2((CW / 2) * 1.02)} ${Y(yN + (yA - yN) * 0.42)}, ${round2(CW / 2)} ${Y(yN)}`;
    }
  }
  return { d: d + ' Z', centers, tips, slot };
}

export type RootCanalTreatmentState = 'NEEDS_TREATMENT' | 'TREATED' | 'UNDERTREATED';

export interface CanalGeometryInput {
  id: string;
  state: RootCanalTreatmentState;
}

export type CanalNode =
  | { kind: 'line'; cls: string; d: string; canalId: string }
  | { kind: 'dot'; x: number; y: number; treat: boolean; canalId: string };

/** Ported verbatim from the mockup's `canalNodes()` — the three treatment states (FR-066a): red
 * ("do leczenia"), green ("wyleczony"), green-body/red-apex ("niedoleczony" — the apex/body color
 * split IS the non-color cue, research.md D10). */
export function canalNodes(
  canals: readonly CanalGeometryInput[],
  rg: RootGeometry,
  crownH: number,
  rootH: number,
  dir: number,
  nRoots: number,
): CanalNode[] {
  const Y = (v: number): number => dir * v;
  const per = new Array(nRoots).fill(0);
  canals.forEach((_, i) => per[i % nRoots]++);
  const used = new Array(nRoots).fill(0);
  const out: CanalNode[] = [];
  canals.forEach((c, i) => {
    const ri = i % nRoots;
    const k = used[ri]++;
    const spread = per[ri] === 1 ? 0 : (k - (per[ri] - 1) / 2) * rg.slot * 0.26;
    const x0 = rg.centers[ri] + spread;
    const y0 = Y(crownH + rootH * 0.14);
    const x1 = rg.tips[ri] + spread * 0.4;
    const y1 = Y(crownH + rootH * 0.9);
    const at = (t: number): [number, number] => [round2(x0 + (x1 - x0) * t), round2(y0 + (y1 - y0) * t)];
    if (c.state === 'UNDERTREATED') {
      const [mx, my] = at(0.58);
      out.push({ kind: 'line', cls: 'canal c-under-body', d: `M ${round2(x0)} ${round2(y0)} L ${mx} ${my}`, canalId: c.id });
      out.push({ kind: 'line', cls: 'canal c-under-apex', d: `M ${mx} ${my} L ${round2(x1)} ${round2(y1)}`, canalId: c.id });
      const [dx, dy] = at(1);
      out.push({ kind: 'dot', x: dx, y: dy, treat: false, canalId: c.id });
    } else {
      const cls = 'canal ' + (c.state === 'TREATED' ? 'c-done' : 'c-treat');
      out.push({ kind: 'line', cls, d: `M ${round2(x0)} ${round2(y0)} L ${round2(x1)} ${round2(y1)}`, canalId: c.id });
      if (c.state === 'NEEDS_TREATMENT') {
        const [dx, dy] = at(1);
        out.push({ kind: 'dot', x: dx, y: dy, treat: true, canalId: c.id });
      }
    }
  });
  return out;
}

export interface ZoneDef {
  surface: ToothSurfaceName;
  d: string;
}

/** Ported from the mockup's `zoneDefs()` — top = vestibular, bottom = lingual/palatal, sides =
 * mesial/distal depending on arch side (FR-026/FR-029), center = occlusal/incisal. */
export function zoneDefs(size: number, right: boolean): ZoneDef[] {
  const o = size / 2;
  const i = size * 0.2;
  const mesialLeft = !right;
  return [
    { surface: 'VESTIBULAR', d: `M ${-o} ${-o} L ${o} ${-o} L ${i} ${-i} L ${-i} ${-i} Z` },
    { surface: 'LINGUAL_PALATAL', d: `M ${-o} ${o} L ${o} ${o} L ${i} ${i} L ${-i} ${i} Z` },
    { surface: mesialLeft ? 'MESIAL' : 'DISTAL', d: `M ${-o} ${-o} L ${-i} ${-i} L ${-i} ${i} L ${-o} ${o} Z` },
    { surface: mesialLeft ? 'DISTAL' : 'MESIAL', d: `M ${o} ${-o} L ${i} ${-i} L ${i} ${i} L ${o} ${o} Z` },
    { surface: 'OCCLUSAL_INCISAL', d: `M ${-i} ${-i} L ${i} ${-i} L ${i} ${i} L ${-i} ${i} Z` },
  ];
}
