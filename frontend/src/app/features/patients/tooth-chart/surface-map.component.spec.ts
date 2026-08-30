import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { SurfaceMapComponent } from './surface-map.component';

describe('SurfaceMapComponent', () => {
  let fixture: ComponentFixture<SurfaceMapComponent>;

  async function setup(fdiNumber: number): Promise<void> {
    await TestBed.configureTestingModule({ imports: [SurfaceMapComponent] }).compileComponents();
    fixture = TestBed.createComponent(SurfaceMapComponent);
    fixture.componentRef.setInput('fdiNumber', fdiNumber);
    fixture.detectChanges();
  }

  it('offers an incisal-lettered zone (I) for an incisor, never occlusal (O)', async () => {
    await setup(11);

    const occlusalIncisalZone = fixture.nativeElement.querySelector(
      '[data-testid="surface-zone-OCCLUSAL_INCISAL"]',
    );
    expect(occlusalIncisalZone).toBeTruthy();
    // labels are off by default (main-diagram usage) — verify via aria-label text instead
    expect(occlusalIncisalZone.getAttribute('aria-label')).toContain('sieczna');
  });

  it('offers an occlusal-lettered zone (O) for a molar, never incisal (I)', async () => {
    await setup(16);

    const occlusalIncisalZone = fixture.nativeElement.querySelector(
      '[data-testid="surface-zone-OCCLUSAL_INCISAL"]',
    );
    expect(occlusalIncisalZone.getAttribute('aria-label')).toContain('żująca');
  });

  it('renders exactly five zones, one per FR-024 surface', async () => {
    await setup(36);

    const zones = fixture.nativeElement.querySelectorAll('[data-testid^="surface-zone-"]');
    expect(zones.length).toBe(5);
  });

  it('clicking a zone toggles its selection', async () => {
    await setup(36);
    let emitted: string | undefined;
    fixture.componentInstance.surfaceToggled.subscribe((s: string) => (emitted = s));

    fixture.nativeElement.querySelector('[data-testid="surface-zone-MESIAL"]').dispatchEvent(new Event('click'));

    expect(emitted).toBe('MESIAL');
  });

  it('shows letter labels only when showLabels is true (FR-029a)', async () => {
    await setup(36);
    expect(fixture.nativeElement.querySelectorAll('.zone-label').length).toBe(0);

    fixture.componentRef.setInput('showLabels', true);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.zone-label').length).toBe(5);
  });

  it('marks a selected zone distinctly from an empty or has-entry zone', async () => {
    await setup(36);
    fixture.componentRef.setInput('selectedSurfaces', ['MESIAL']);
    fixture.componentRef.setInput('existingSurfaces', ['DISTAL']);
    fixture.detectChanges();

    const mesial = fixture.nativeElement.querySelector('[data-testid="surface-zone-MESIAL"]');
    const distal = fixture.nativeElement.querySelector('[data-testid="surface-zone-DISTAL"]');
    const vestibular = fixture.nativeElement.querySelector('[data-testid="surface-zone-VESTIBULAR"]');

    expect(mesial.classList.contains('selected')).toBe(true);
    expect(distal.classList.contains('has-entry')).toBe(true);
    expect(vestibular.classList.contains('selected')).toBe(false);
    expect(vestibular.classList.contains('has-entry')).toBe(false);
  });
});
