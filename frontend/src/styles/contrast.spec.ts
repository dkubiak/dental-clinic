import { describe, expect, it } from 'vitest';
import { contrastRatio } from './contrast';

describe('contrastRatio', () => {
  it('daje 21.00 dla czerni na bieli (skrajny przypadek)', () => {
    expect(contrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21.0, 2);
  });

  it('daje 1.00 dla identycznych kolorów', () => {
    expect(contrastRatio('#FFFFFF', '#FFFFFF')).toBeCloseTo(1.0, 2);
  });

  it('daje 5.90 dla Bronze 800 na papierze (para kontrolna z marki)', () => {
    expect(contrastRatio('#7A5A2E', '#FAF7F2')).toBeCloseTo(5.9, 2);
  });

  it('daje 1.99 dla Gold 400 na papierze — pułapka opisana w design/brand/README.md', () => {
    expect(contrastRatio('#CBAD89', '#FAF7F2')).toBeCloseTo(1.99, 2);
  });

  it('jest symetryczna względem kolejności argumentów', () => {
    expect(contrastRatio('#7A5A2E', '#FAF7F2')).toBeCloseTo(contrastRatio('#FAF7F2', '#7A5A2E'), 5);
  });
});
