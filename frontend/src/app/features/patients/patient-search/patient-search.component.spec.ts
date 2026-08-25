import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PatientsService } from '../patients.service';
import { PatientSearchComponent } from './patient-search.component';

describe('PatientSearchComponent', () => {
  let fixture: ComponentFixture<PatientSearchComponent>;
  let component: PatientSearchComponent;
  let patientsService: { search: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    patientsService = { search: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [PatientSearchComponent],
      providers: [
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: PatientsService, useValue: patientsService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PatientSearchComponent);
    component = fixture.componentInstance;
  });

  it('finds an existing patient by last-name fragment (FR-012)', () => {
    patientsService.search.mockReturnValue(
      of([
        {
          id: 'p1',
          firstName: 'Jan',
          lastName: 'Kowalski',
          dateOfBirth: '1990-01-15',
          pesel: '90011500013',
        },
      ]),
    );

    component.searchControl.setValue('Kowalski');
    component.search();
    fixture.detectChanges();

    expect(patientsService.search).toHaveBeenCalledWith('Kowalski');
    expect(component.results()).toHaveLength(1);
    expect(component.results()[0].lastName).toBe('Kowalski');

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Kowalski');
  });

  it('shows an empty-state message when nothing matches', () => {
    patientsService.search.mockReturnValue(of([]));

    component.searchControl.setValue('NoSuchPatient');
    component.search();
    fixture.detectChanges();

    expect(component.results()).toHaveLength(0);
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Brak wyników');
  });

  it('does not call the backend for a blank query', () => {
    component.searchControl.setValue('   ');
    component.search();

    expect(patientsService.search).not.toHaveBeenCalled();
  });

  it('triggers a search on a real form submit event (not just calling component.search()) — T063 finding', () => {
    // Only ReactiveFormsModule is imported (no [formGroup] on this bare-FormControl form), so
    // (ngSubmit) never actually bound to anything and the browser's native, un-prevented GET
    // form submission fired instead — this test drives the actual DOM <form> submit event to
    // catch that class of bug, which calling component.search()/onSubmit() directly cannot.
    patientsService.search.mockReturnValue(of([]));
    fixture.detectChanges();

    const input: HTMLInputElement = fixture.nativeElement.querySelector('[data-testid="search-input"]');
    input.value = 'Kowalski';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    const submitEvent = new Event('submit', { cancelable: true, bubbles: true });
    form.dispatchEvent(submitEvent);

    expect(patientsService.search).toHaveBeenCalledWith('Kowalski');
    expect(submitEvent.defaultPrevented).toBe(true);
  });
});
