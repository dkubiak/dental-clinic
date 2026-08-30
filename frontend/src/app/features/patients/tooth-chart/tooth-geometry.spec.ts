import { describe, expect, it } from 'vitest';
import {
  ALL_SURFACES,
  CROWN_ROOT_RATIO,
  canalNodes,
  crownPath,
  rootGeometry,
  surfaceNamePl,
  toothAnatomy,
  zoneDefs,
} from './tooth-geometry';

describe('tooth-geometry', () => {
  describe('toothAnatomy', () => {
    it('classifies permanent incisors, canines, premolars, molars by FDI position digit', () => {
      expect(toothAnatomy(11).toothType).toBe('INCISOR');
      expect(toothAnatomy(12).toothType).toBe('INCISOR');
      expect(toothAnatomy(13).toothType).toBe('CANINE');
      expect(toothAnatomy(14).toothType).toBe('PREMOLAR');
      expect(toothAnatomy(15).toothType).toBe('PREMOLAR');
      expect(toothAnatomy(16).toothType).toBe('MOLAR');
      expect(toothAnatomy(18).toothType).toBe('MOLAR');
    });

    it('classifies deciduous teeth with no premolars (position 4-5 are molars)', () => {
      expect(toothAnatomy(51).toothType).toBe('INCISOR');
      expect(toothAnatomy(53).toothType).toBe('CANINE');
      expect(toothAnatomy(54).toothType).toBe('MOLAR');
      expect(toothAnatomy(55).toothType).toBe('MOLAR');
      expect(toothAnatomy(51).deciduous).toBe(true);
    });

    it('derives root count per FR anatomical rules (upper first premolar and molars are multi-rooted)', () => {
      expect(toothAnatomy(11).roots).toBe(1); // incisor
      expect(toothAnatomy(13).roots).toBe(1); // canine
      expect(toothAnatomy(14).roots).toBe(2); // upper first premolar
      expect(toothAnatomy(15).roots).toBe(1); // upper second premolar
      expect(toothAnatomy(44).roots).toBe(1); // lower premolar
      expect(toothAnatomy(16).roots).toBe(3); // upper molar
      expect(toothAnatomy(46).roots).toBe(2); // lower molar
    });

    it('derives upper/lower and left/right from the FDI quadrant', () => {
      expect(toothAnatomy(11).upper).toBe(true);
      expect(toothAnatomy(11).right).toBe(true);
      expect(toothAnatomy(41).upper).toBe(false);
      expect(toothAnatomy(41).right).toBe(true);
      expect(toothAnatomy(21).right).toBe(false);
    });
  });

  describe('surfaceNamePl (FR-024/FR-025)', () => {
    it('offers sieczna for incisors/canines and żująca for premolars/molars — never both', () => {
      expect(surfaceNamePl(11, 'OCCLUSAL_INCISAL')).toBe('sieczna');
      expect(surfaceNamePl(13, 'OCCLUSAL_INCISAL')).toBe('sieczna');
      expect(surfaceNamePl(14, 'OCCLUSAL_INCISAL')).toBe('żująca');
      expect(surfaceNamePl(16, 'OCCLUSAL_INCISAL')).toBe('żująca');
    });

    it('names the lingual/palatal surface podniebienna for upper teeth, językowa for lower', () => {
      expect(surfaceNamePl(11, 'LINGUAL_PALATAL')).toBe('podniebienna');
      expect(surfaceNamePl(41, 'LINGUAL_PALATAL')).toBe('językowa');
    });

    it('names the vestibular surface wargowa for anterior teeth, policzkowa for posterior', () => {
      expect(surfaceNamePl(11, 'VESTIBULAR')).toBe('wargowa');
      expect(surfaceNamePl(13, 'VESTIBULAR')).toBe('wargowa');
      expect(surfaceNamePl(16, 'VESTIBULAR')).toBe('policzkowa');
    });
  });

  it('every tooth type has a crown/root ratio and a cusp count', () => {
    for (const type of ['INCISOR', 'CANINE', 'PREMOLAR', 'MOLAR'] as const) {
      expect(CROWN_ROOT_RATIO[type].cusps).toBeGreaterThan(0);
      expect(CROWN_ROOT_RATIO[type].crown).toBeGreaterThan(0);
      expect(CROWN_ROOT_RATIO[type].root).toBeGreaterThan(0);
    }
  });

  it('crownPath produces a closed SVG path string', () => {
    const d = crownPath(20, 14, 3, 1, 0.09);
    expect(d.startsWith('M ')).toBe(true);
    expect(d.trim().endsWith('Z')).toBe(true);
  });

  it('rootGeometry produces one center/tip per root', () => {
    const rg = rootGeometry(20, 14, 22, 3, 1);
    expect(rg.centers).toHaveLength(3);
    expect(rg.tips).toHaveLength(3);
    expect(rg.d.trim().endsWith('Z')).toBe(true);
  });

  describe('canalNodes', () => {
    it('renders a single line for a NEEDS_TREATMENT canal, plus a dot', () => {
      const rg = rootGeometry(20, 14, 22, 1, 1);
      const nodes = canalNodes([{ id: 'c1', state: 'NEEDS_TREATMENT' }], rg, 14, 22, 1, 1);
      expect(nodes.filter((n) => n.kind === 'line')).toHaveLength(1);
      expect(nodes.filter((n) => n.kind === 'dot')).toHaveLength(1);
    });

    it('renders a body+apex split for UNDERTREATED — the split itself is the non-color cue', () => {
      const rg = rootGeometry(20, 14, 22, 1, 1);
      const nodes = canalNodes([{ id: 'c1', state: 'UNDERTREATED' }], rg, 14, 22, 1, 1);
      const lines = nodes.filter((n) => n.kind === 'line') as Array<{ kind: 'line'; cls: string }>;
      expect(lines).toHaveLength(2);
      expect(lines.map((l) => l.cls)).toEqual(
        expect.arrayContaining([expect.stringContaining('c-under-body'), expect.stringContaining('c-under-apex')]),
      );
    });

    it('renders a plain line with no dot for a TREATED canal', () => {
      const rg = rootGeometry(20, 14, 22, 1, 1);
      const nodes = canalNodes([{ id: 'c1', state: 'TREATED' }], rg, 14, 22, 1, 1);
      expect(nodes.filter((n) => n.kind === 'line')).toHaveLength(1);
      expect(nodes.filter((n) => n.kind === 'dot')).toHaveLength(0);
    });
  });

  describe('zoneDefs', () => {
    it('covers all five FR-024 surfaces exactly once', () => {
      const zones = zoneDefs(40, true);
      expect(zones.map((z) => z.surface).sort()).toEqual([...ALL_SURFACES].sort());
    });

    it('flips mesial/distal sides depending on which side of the arch the tooth is on', () => {
      const rightZones = zoneDefs(40, true);
      const leftZones = zoneDefs(40, false);
      const rightMesial = rightZones.find((z) => z.surface === 'MESIAL');
      const leftMesial = leftZones.find((z) => z.surface === 'MESIAL');
      expect(rightMesial?.d).not.toBe(leftMesial?.d);
    });
  });
});
